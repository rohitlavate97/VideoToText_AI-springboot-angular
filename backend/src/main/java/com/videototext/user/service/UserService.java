package com.videototext.user.service;

import com.videototext.common.exception.ResourceNotFoundException;
import com.videototext.user.dto.UpdatePreferenceRequest;
import com.videototext.user.dto.UpdateProfileRequest;
import com.videototext.user.dto.UserPreferenceResponse;
import com.videototext.user.dto.UserProfileResponse;
import com.videototext.user.entity.Role;
import com.videototext.user.entity.User;
import com.videototext.user.entity.UserPreference;
import com.videototext.user.repository.UserPreferenceRepository;
import com.videototext.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UUID userId) {
        User user = findUserById(userId);
        return mapToProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateUserProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUserById(userId);

        if (request.getFirstName() != null && !request.getFirstName().isBlank()) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null && !request.getLastName().isBlank()) {
            user.setLastName(request.getLastName().trim());
        }

        User updatedUser = userRepository.save(user);
        return mapToProfileResponse(updatedUser);
    }

    @Transactional
    public UserPreferenceResponse updateUserPreferences(UUID userId, UpdatePreferenceRequest request) {
        UserPreference preference = userPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = findUserById(userId);
                    return UserPreference.builder()
                            .user(user)
                            .defaultLanguage("en")
                            .processingMode("local")
                            .preferredAsrEngine("faster-whisper")
                            .preferredAsrModel("small")
                            .build();
                });

        if (request.getDefaultLanguage() != null && !request.getDefaultLanguage().isBlank()) {
            preference.setDefaultLanguage(request.getDefaultLanguage().trim());
        }
        if (request.getProcessingMode() != null && !request.getProcessingMode().isBlank()) {
            preference.setProcessingMode(request.getProcessingMode().trim());
        }
        if (request.getPreferredAsrEngine() != null && !request.getPreferredAsrEngine().isBlank()) {
            preference.setPreferredAsrEngine(request.getPreferredAsrEngine().trim());
        }
        if (request.getPreferredAsrModel() != null && !request.getPreferredAsrModel().isBlank()) {
            preference.setPreferredAsrModel(request.getPreferredAsrModel().trim());
        }

        UserPreference saved = userPreferenceRepository.save(preference);
        return mapToPreferenceResponse(saved);
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        UserPreference pref = user.getPreferences();
        UserPreferenceResponse prefResponse = pref != null ? mapToPreferenceResponse(pref) : null;

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toList()))
                .preferences(prefResponse)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private UserPreferenceResponse mapToPreferenceResponse(UserPreference pref) {
        return UserPreferenceResponse.builder()
                .defaultLanguage(pref.getDefaultLanguage())
                .processingMode(pref.getProcessingMode())
                .preferredAsrEngine(pref.getPreferredAsrEngine())
                .preferredAsrModel(pref.getPreferredAsrModel())
                .build();
    }
}
