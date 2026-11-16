package com.videototext.security;

import com.videototext.user.entity.Role;
import com.videototext.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private CustomUserDetails userDetails;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", "my-super-secret-key-that-is-at-least-64-characters-long-for-testing-purpose-1234567890!");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", 900000L); // 15 mins
        ReflectionTestUtils.setField(tokenProvider, "refreshExpirationMs", 604800000L); // 7 days
        tokenProvider.init();

        Role role = Role.builder().name("ROLE_USER").build();
        User user = User.builder()
                .email("rohit@example.com")
                .passwordHash("hashedpassword")
                .firstName("Rohit")
                .lastName("Sharma")
                .enabled(true)
                .roles(Set.of(role))
                .build();
        user.setId(userId);

        userDetails = new CustomUserDetails(user);
    }

    @Test
    void shouldGenerateValidAccessToken() {
        String token = tokenProvider.generateAccessToken(userDetails);

        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
        assertEquals("rohit@example.com", tokenProvider.getEmailFromToken(token));
        assertEquals(userId, tokenProvider.getUserIdFromToken(token));
        assertEquals("access", tokenProvider.getTokenType(token));
    }

    @Test
    void shouldGenerateValidRefreshToken() {
        String refreshToken = tokenProvider.generateRefreshToken(userDetails);

        assertNotNull(refreshToken);
        assertTrue(tokenProvider.validateToken(refreshToken));
        assertEquals("rohit@example.com", tokenProvider.getEmailFromToken(refreshToken));
        assertEquals(userId, tokenProvider.getUserIdFromToken(refreshToken));
        assertEquals("refresh", tokenProvider.getTokenType(refreshToken));
    }

    @Test
    void shouldRejectInvalidToken() {
        assertFalse(tokenProvider.validateToken("invalid.token.signature"));
        assertFalse(tokenProvider.validateToken(""));
        assertFalse(tokenProvider.validateToken(null));
    }
}
