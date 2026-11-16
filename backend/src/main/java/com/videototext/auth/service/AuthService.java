package com.videototext.auth.service;

import com.videototext.auth.dto.AuthResponse;
import com.videototext.auth.dto.LoginRequest;
import com.videototext.auth.dto.RefreshTokenRequest;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.common.exception.ConflictException;
import com.videototext.common.exception.UnauthorizedException;
import com.videototext.security.CustomUserDetails;
import com.videototext.security.JwtTokenProvider;
import com.videototext.user.entity.Role;
import com.videototext.user.entity.User;
import com.videototext.user.entity.UserPreference;
import com.videototext.user.repository.RoleRepository;
import com.videototext.user.repository.UserPreferenceRepository;
import com.videototext.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email is already registered: " + request.getEmail());
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        User savedUser = userRepository.save(user);

        UserPreference preference = UserPreference.builder()
                .user(savedUser)
                .defaultLanguage("en")
                .processingMode("local")
                .preferredAsrEngine("faster-whisper")
                .preferredAsrModel("small")
                .build();
        userPreferenceRepository.save(preference);
        savedUser.setPreferences(preference);

        CustomUserDetails userDetails = new CustomUserDetails(savedUser);
        return buildAuthResponse(userDetails);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail().toLowerCase().trim(), request.getPassword())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            return buildAuthResponse(userDetails);
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt for email: {}", request.getEmail());
            throw new UnauthorizedException("Invalid email or password");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();

        if (!tokenProvider.validateToken(token)) {
            throw new UnauthorizedException("Refresh token is invalid or has expired");
        }

        if (!"refresh".equalsIgnoreCase(tokenProvider.getTokenType(token))) {
            throw new UnauthorizedException("Provided token is not a refresh token");
        }

        String email = tokenProvider.getEmailFromToken(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User associated with refresh token not found"));

        if (!user.isEnabled()) {
            throw new UnauthorizedException("User account is disabled");
        }

        // Check if token was explicitly revoked in Redis
        try {
            Boolean isRevoked = (Boolean) redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + token + ":revoked");
            if (Boolean.TRUE.equals(isRevoked)) {
                throw new UnauthorizedException("Refresh token has been revoked");
            }
            // Rotate: revoke old token
            redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + token + ":revoked", true, Duration.ofDays(7));
        } catch (Exception ex) {
            log.warn("Redis unavailable for refresh token revocation check: {}", ex.getMessage());
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        return buildAuthResponse(userDetails);
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken + ":revoked", true, Duration.ofDays(7));
            } catch (Exception ex) {
                log.warn("Failed to record token revocation in Redis: {}", ex.getMessage());
            }
        }
    }

    private AuthResponse buildAuthResponse(CustomUserDetails userDetails) {
        String accessToken = tokenProvider.generateAccessToken(userDetails);
        String refreshToken = tokenProvider.generateRefreshToken(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getExpirationDurationMs() / 1000)
                .user(AuthResponse.UserSummary.builder()
                        .id(userDetails.getId())
                        .email(userDetails.getUsername())
                        .firstName(userDetails.getFullName().split(" ")[0])
                        .lastName(userDetails.getFullName().contains(" ") ? userDetails.getFullName().substring(userDetails.getFullName().indexOf(" ") + 1) : "")
                        .roles(roles)
                        .build())
                .build();
    }
}
