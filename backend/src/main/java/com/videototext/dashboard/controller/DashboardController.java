package com.videototext.dashboard.controller;

import com.videototext.common.dto.ApiResponse;
import com.videototext.dashboard.dto.DashboardStatsResponse;
import com.videototext.dashboard.service.DashboardService;
import com.videototext.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard Analytics", description = "User dashboard aggregated statistics, job volume, and activity tracking")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Get user dashboard statistics", description = "Returns aggregated metrics including total videos, duration, words, language distribution, and recent activity")
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        DashboardStatsResponse stats = dashboardService.getUserStats(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
