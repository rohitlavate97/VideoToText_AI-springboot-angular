package com.videototext.user.controller;

import com.videototext.common.dto.ApiResponse;
import com.videototext.security.CustomUserDetails;
import com.videototext.user.dto.UpdatePreferenceRequest;
import com.videototext.user.dto.UpdateProfileRequest;
import com.videototext.user.dto.UserPreferenceResponse;
import com.videototext.user.dto.UserProfileResponse;
import com.videototext.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Management", description = "Current user profile and processing preferences APIs")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get current user profile", description = "Retrieves profile details and preferences of the authenticated user")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserProfileResponse profile = userService.getUserProfile(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(profile, "Profile retrieved successfully"));
    }

    @Operation(summary = "Update user profile", description = "Updates first name and last name of the authenticated user")
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse updated = userService.updateUserProfile(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Profile updated successfully"));
    }

    @Operation(summary = "Update user preferences", description = "Updates language and processing preferences")
    @PutMapping("/me/preferences")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updatePreferences(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdatePreferenceRequest request) {
        UserPreferenceResponse preferences = userService.updateUserPreferences(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(preferences, "Preferences updated successfully"));
    }
}
