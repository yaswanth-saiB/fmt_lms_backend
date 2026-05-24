package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.repository.UserRepository;
import com.fmt.fmt_backend.service.CampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns", description = "WhatsApp bulk campaign management — Admin only")
public class CampaignController {

    private final CampaignService campaignService;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Create a new campaign (DRAFT)")
    public ResponseEntity<ApiResponse<CampaignSummaryResponse>> create(
            @RequestBody CampaignRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = extractUserId(principal);
        return ResponseEntity.ok(ApiResponse.success("Campaign created", campaignService.createCampaign(req, userId)));
    }

    @GetMapping
    @Operation(summary = "List all campaigns")
    public ResponseEntity<ApiResponse<List<CampaignSummaryResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Campaigns fetched", campaignService.getCampaigns()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Campaign detail with recipients")
    public ResponseEntity<ApiResponse<CampaignDetailResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Campaign fetched", campaignService.getCampaignDetail(id)));
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview how many leads match the filter before sending")
    public ResponseEntity<ApiResponse<CampaignPreviewResponse>> preview(
            @RequestBody CampaignRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Preview ready", campaignService.previewCampaign(req)));
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Send the campaign to all matching leads")
    public ResponseEntity<ApiResponse<CampaignDetailResponse>> send(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = extractUserId(principal);
        return ResponseEntity.ok(ApiResponse.success("Campaign sent", campaignService.sendCampaign(id, userId)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a DRAFT campaign")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.ok(ApiResponse.success("Campaign deleted"));
    }

    private UUID extractUserId(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
