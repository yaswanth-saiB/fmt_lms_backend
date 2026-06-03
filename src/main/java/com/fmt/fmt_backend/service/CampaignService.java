package com.fmt.fmt_backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmt.fmt_backend.dto.CampaignDetailResponse;
import com.fmt.fmt_backend.dto.CampaignPreviewResponse;
import com.fmt.fmt_backend.dto.CampaignRequest;
import com.fmt.fmt_backend.dto.CampaignSummaryResponse;
import com.fmt.fmt_backend.entity.*;
import com.fmt.fmt_backend.enums.*;
import com.fmt.fmt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final WhatsappCampaignRepository campaignRepository;
    private final WhatsappCampaignRecipientRepository recipientRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final WhatsAppApiService whatsAppApiService;
    private final ObjectMapper objectMapper;
    private final CampaignLeadSenderService campaignLeadSender;

    // ─── Create ──────────────────────────────────────────────────────────────────

    @Transactional
    public CampaignSummaryResponse createCampaign(CampaignRequest req, UUID createdByUserId) {
        User creator = userRepository.findById(createdByUserId).orElse(null);

        WhatsappCampaign campaign = WhatsappCampaign.builder()
                .name(req.getName())
                .description(req.getDescription())
                .templateName(req.getTemplateName())
                .templateParams(serializeParams(req.getTemplateParams()))
                .templateParamNames(serializeParams(
                        (req.getTemplateParamNames() != null && !req.getTemplateParamNames().isEmpty())
                                ? req.getTemplateParamNames()
                                : whatsAppApiService.getParamNamesForTemplate(req.getTemplateName())))
                .leadStatuses(serializeStatuses(req.getLeadStatuses()))
                .courseInterest(req.getCourseInterest())
                .createdBy(creator)
                .build();

        campaign = campaignRepository.save(campaign);
        log.info("Campaign created: {} by {}", campaign.getId(), createdByUserId);
        return toSummary(campaign);
    }

    // ─── List ─────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> getCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toSummary).collect(Collectors.toList());
    }

    // ─── Detail ───────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CampaignDetailResponse getCampaignDetail(UUID campaignId) {
        WhatsappCampaign campaign = findCampaign(campaignId);
        List<WhatsappCampaignRecipient> recipients =
                recipientRepository.findByCampaignOrderBySentAtDesc(campaign);
        return toDetail(campaign, recipients);
    }

    // ─── Preview ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CampaignPreviewResponse previewCampaign(CampaignRequest req) {
        List<LeadStatus> statuses = deserializeStatuses(serializeStatuses(req.getLeadStatuses()));
        long count = leadRepository.count(buildSpec(statuses, req.getCourseInterest()));
        return CampaignPreviewResponse.builder()
                .leadCount(count)
                .message(count == 0
                        ? "No leads match the selected filters"
                        : count + " leads will receive this campaign")
                .build();
    }

    // ─── Send ─────────────────────────────────────────────────────────────────────

    @Transactional
    public CampaignDetailResponse sendCampaign(UUID campaignId, UUID sentByUserId) {
        WhatsappCampaign campaign = findCampaign(campaignId);

        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Campaign is already " + campaign.getStatus().name().toLowerCase() + " — cannot resend");
        }

        List<LeadStatus> statuses = deserializeStatuses(campaign.getLeadStatuses());
        List<Lead> leads = leadRepository.findAll(buildSpec(statuses, campaign.getCourseInterest()));

        if (leads.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No leads match the campaign filters");
        }

        campaign.setStatus(CampaignStatus.SENDING);
        campaign.setTotalCount(leads.size());
        campaignRepository.save(campaign);

        List<String> params = deserializeParams(campaign.getTemplateParams());
        List<String> paramNames = deserializeParams(campaign.getTemplateParamNames());

        String headerImageHandle = whatsAppApiService.getApprovedTemplates().stream()
                .filter(t -> campaign.getTemplateName().equals(t.getName()))
                .map(t -> t.getHeaderImageHandle())
                .filter(h -> h != null)
                .findFirst().orElse(null);

        int success = 0;
        int failed = 0;
        List<WhatsappCampaignRecipient> recipients = new ArrayList<>();

        for (Lead lead : leads) {
            List<String> resolved = resolveParams(params, lead);
            // Each lead runs in its own REQUIRES_NEW transaction so it commits immediately.
            // Webhooks can find the conversation/message as soon as this call returns.
            // Passing IDs (not entities) so the inner transaction loads managed instances — avoids detached-entity errors.
            WhatsappCampaignRecipient r = campaignLeadSender.sendToLead(
                    campaignId, lead.getId(), resolved, paramNames, headerImageHandle, sentByUserId);
            recipients.add(r);
            if (Boolean.TRUE.equals(r.getFailed())) failed++; else success++;
        }

        campaign.setSuccessCount(success);
        campaign.setFailCount(failed);
        campaign.setStatus(success == 0 ? CampaignStatus.FAILED
                : failed > 0 ? CampaignStatus.PARTIAL_FAIL
                : CampaignStatus.SENT);
        campaign.setSentAt(LocalDateTime.now());
        campaignRepository.save(campaign);

        log.info("Campaign {} sent: {} success {} failed", campaignId, success, failed);
        return toDetail(campaign, recipients);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteCampaign(UUID campaignId, boolean isAdmin) {
        WhatsappCampaign campaign = findCampaign(campaignId);
        CampaignStatus status = campaign.getStatus();

        // Never allow deletion of an in-flight campaign — would leave inconsistent state.
        if (status == CampaignStatus.SENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot delete a campaign while it's actively being sent");
        }

        // SALES: only DRAFT and FAILED (low-stakes states with no successful sends).
        // ADMIN: any non-SENDING status — including SENT and PARTIAL_FAIL.
        //        Use case: deleting small test campaigns after verifying delivery.
        if (!isAdmin && status != CampaignStatus.DRAFT && status != CampaignStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only admins can delete a " + status + " campaign");
        }

        // Delete recipients first (FK constraint), then the campaign
        List<WhatsappCampaignRecipient> recipients = recipientRepository.findByCampaignOrderBySentAtDesc(campaign);
        recipientRepository.deleteAll(recipients);
        campaignRepository.delete(campaign);
        log.info("Campaign {} ({}, status={}) deleted by {}",
                campaign.getName(), campaignId, status, isAdmin ? "ADMIN" : "SALES");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private WhatsappCampaign findCampaign(UUID id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    private Specification<Lead> buildSpec(List<LeadStatus> statuses, String courseInterest) {
        Specification<Lead> spec = Specification.where(
                (root, q, cb) -> cb.isNotNull(root.get("phone")));
        if (statuses != null && !statuses.isEmpty()) {
            spec = spec.and((root, q, cb) -> root.get("status").in(statuses));
        }
        if (courseInterest != null && !courseInterest.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("courseInterest"), courseInterest));
        }
        return spec;
    }

    private List<String> resolveParams(List<String> params, Lead lead) {
        if (params == null) return List.of();
        String name = lead.getName();
        if (name == null || name.isBlank() || name.equalsIgnoreCase("Unknown")) {
            // Fall back to phone so Meta never receives an empty text parameter
            name = lead.getPhone() != null ? lead.getPhone() : "Customer";
        }
        final String resolvedName = name;
        return params.stream()
                .map(p -> p.replace("{{name}}", resolvedName))
                .collect(Collectors.toList());
    }

    private String serializeParams(List<String> params) {
        if (params == null || params.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(params); } catch (Exception e) { return null; }
    }

    private List<String> deserializeParams(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return List.of(); }
    }

    private String serializeStatuses(List<LeadStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) return null;
        return statuses.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private List<LeadStatus> deserializeStatuses(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(LeadStatus::valueOf)
                .collect(Collectors.toList());
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    private CampaignSummaryResponse toSummary(WhatsappCampaign c) {
        String createdBy = c.getCreatedBy() != null
                ? c.getCreatedBy().getFirstName() + " " + c.getCreatedBy().getLastName() : null;
        return CampaignSummaryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .templateName(c.getTemplateName())
                .leadStatuses(c.getLeadStatuses())
                .courseInterest(c.getCourseInterest())
                .status(c.getStatus())
                .totalCount(c.getTotalCount())
                .successCount(c.getSuccessCount())
                .failCount(c.getFailCount())
                .createdByName(createdBy)
                .createdAt(c.getCreatedAt())
                .sentAt(c.getSentAt())
                .build();
    }

    private CampaignDetailResponse toDetail(WhatsappCampaign c, List<WhatsappCampaignRecipient> recipients) {
        String createdBy = c.getCreatedBy() != null
                ? c.getCreatedBy().getFirstName() + " " + c.getCreatedBy().getLastName() : null;

        long deliveredCount = recipients.stream().filter(r -> !Boolean.TRUE.equals(r.getFailed())
                && (r.getDeliveryStatus() == WaMessageStatus.DELIVERED || r.getDeliveryStatus() == WaMessageStatus.READ)).count();
        long readCount = recipients.stream().filter(r -> !Boolean.TRUE.equals(r.getFailed())
                && r.getDeliveryStatus() == WaMessageStatus.READ).count();
        long repliedCount = recipients.stream().filter(r -> Boolean.TRUE.equals(r.getReplied())).count();

        List<CampaignDetailResponse.RecipientResponse> recipientResponses = recipients.stream()
                .map(r -> CampaignDetailResponse.RecipientResponse.builder()
                        .id(r.getId())
                        .leadId(r.getLead() != null ? r.getLead().getId() : null)
                        .leadName(r.getLeadName())
                        .phone(r.getPhone())
                        .failed(r.getFailed())
                        .errorMessage(r.getErrorMessage())
                        .waMessageId(r.getWaMessageId())
                        .sentAt(r.getSentAt())
                        .deliveryStatus(r.getDeliveryStatus() != null ? r.getDeliveryStatus().name() : null)
                        .replied(r.getReplied())
                        .repliedAt(r.getRepliedAt())
                        .build())
                .collect(Collectors.toList());

        return CampaignDetailResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .templateName(c.getTemplateName())
                .templateParams(deserializeParams(c.getTemplateParams()))
                .leadStatuses(c.getLeadStatuses())
                .courseInterest(c.getCourseInterest())
                .status(c.getStatus())
                .totalCount(c.getTotalCount())
                .successCount(c.getSuccessCount())
                .failCount(c.getFailCount())
                .deliveredCount(deliveredCount)
                .readCount(readCount)
                .repliedCount(repliedCount)
                .createdByName(createdBy)
                .createdAt(c.getCreatedAt())
                .sentAt(c.getSentAt())
                .recipients(recipientResponses)
                .build();
    }
}
