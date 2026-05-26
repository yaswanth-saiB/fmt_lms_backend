package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.enums.ConversationStatus;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.service.InboxService;
import com.fmt.fmt_backend.service.WhatsAppApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Inbox", description = "Inbox management — ADMIN or SALES")
public class InboxController {

    private final InboxService inboxService;
    private final WhatsAppApiService whatsAppApiService;
    private final com.fmt.fmt_backend.repository.UserRepository userRepository;

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<Page<ConversationSummaryResponse>>> getConversations(
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String label,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Conversations fetched",
                inboxService.getConversations(status, assignedToMe, search, userId, page, size, label)));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<ConversationDetailResponse>> getConversation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Conversation fetched",
                inboxService.getConversationDetail(id)));
    }

    @PostMapping("/conversations/{id}/reply")
    public ResponseEntity<ApiResponse<MessageResponse>> reply(
            @PathVariable UUID id,
            @Valid @RequestBody InboxReplyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Message sent",
                inboxService.reply(id, request.getMessage(), userId, request.getReplyToMessageId())));
    }

    @PostMapping("/conversations/{id}/send-template")
    public ResponseEntity<ApiResponse<MessageResponse>> sendTemplate(
            @PathVariable UUID id,
            @RequestBody SendTemplateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Template sent",
                inboxService.sendTemplate(id, request.getTemplateName(), request.getParameters(), request.getParamNames(), userId)));
    }

    @PutMapping("/conversations/{id}/assign")
    public ResponseEntity<ApiResponse<Void>> assign(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        inboxService.assignConversation(id, UUID.fromString(body.get("assignedTo")));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/conversations/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        inboxService.updateStatus(id, ConversationStatus.valueOf(body.get("status")));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/conversations/{id}/labels")
    public ResponseEntity<ApiResponse<Void>> updateLabels(
            @PathVariable UUID id,
            @RequestBody Map<String, List<String>> body) {
        inboxService.updateLabels(id, body.get("labels"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/conversations/{id}/toggle-bot")
    public ResponseEntity<ApiResponse<Boolean>> toggleBot(@PathVariable UUID id) {
        boolean active = inboxService.toggleBot(id);
        return ResponseEntity.ok(ApiResponse.success("Bot toggled", active));
    }

    @DeleteMapping("/conversations/{id}/messages")
    @io.swagger.v3.oas.annotations.Operation(summary = "Clear all messages in a conversation and reset bot state (ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> clearMessages(@PathVariable UUID id) {
        inboxService.clearConversationMessages(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        UserRole role = resolveRole(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Unread count fetched",
                inboxService.getUnreadCount(userId, role)));
    }

    // ── Notes ──────────────────────────────────────────────────────────────────

    @PostMapping("/conversations/{id}/notes")
    public ResponseEntity<ApiResponse<NoteResponse>> addNote(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Note added",
                inboxService.addNote(id, body.get("content"), userId)));
    }

    @DeleteMapping("/conversations/{conversationId}/notes/{noteId}")
    public ResponseEntity<ApiResponse<Void>> deleteNote(
            @PathVariable UUID conversationId,
            @PathVariable UUID noteId) {
        inboxService.deleteNote(noteId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Quick replies ───────────────────────────────────────────────────────────

    @GetMapping("/quick-replies")
    public ResponseEntity<ApiResponse<List<QuickReplyResponse>>> getQuickReplies() {
        return ResponseEntity.ok(ApiResponse.success("Quick replies fetched", inboxService.getQuickReplies()));
    }

    @PostMapping("/quick-replies")
    public ResponseEntity<ApiResponse<QuickReplyResponse>> createQuickReply(
            @RequestBody QuickReplyRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Quick reply created",
                inboxService.createQuickReply(req, userId)));
    }

    @PutMapping("/quick-replies/{id}")
    public ResponseEntity<ApiResponse<QuickReplyResponse>> updateQuickReply(
            @PathVariable UUID id,
            @RequestBody QuickReplyRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Quick reply updated",
                inboxService.updateQuickReply(id, req)));
    }

    @DeleteMapping("/quick-replies/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuickReply(@PathVariable UUID id) {
        inboxService.deleteQuickReply(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Direct send to a lead by phone (from CRM lead page) ─────────────────────

    @PostMapping("/direct-send")
    @Operation(summary = "Send a WhatsApp message or template directly to a phone number")
    public ResponseEntity<ApiResponse<Void>> directSend(
            @Valid @RequestBody com.fmt.fmt_backend.dto.DirectSendRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        inboxService.directSend(req.getPhone(), req.getType(), req.getMessage(),
                req.getTemplateName(), req.getParams(), req.getParamNames(), userId);
        return ResponseEntity.ok(ApiResponse.success("Message sent", null));
    }

    // ── Templates ───────────────────────────────────────────────────────────────

    @GetMapping("/templates")
    @Operation(summary = "List approved WhatsApp templates from Meta")
    public ResponseEntity<ApiResponse<List<com.fmt.fmt_backend.dto.WhatsappTemplateDto>>> getTemplates() {
        return ResponseEntity.ok(ApiResponse.success("Templates fetched", whatsAppApiService.getApprovedTemplates()));
    }

    // ── Media proxy ─────────────────────────────────────────────────────────────

    @GetMapping("/media/{mediaId}")
    @Operation(summary = "Proxy WhatsApp media — streams image/document/audio/video to browser")
    public ResponseEntity<byte[]> getMedia(@PathVariable String mediaId) {
        try {
            WhatsAppApiService.MediaDownload dl = whatsAppApiService.downloadMedia(mediaId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(dl.mimeType()));
            return ResponseEntity.ok().headers(headers).body(dl.content());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private UserRole resolveRole(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> UserRole.valueOf(a.getAuthority().replace("ROLE_", "")))
                .orElse(UserRole.SALES);
    }
}
