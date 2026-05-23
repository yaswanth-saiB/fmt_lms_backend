package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.dto.ChatbotResponseDto;
import com.fmt.fmt_backend.dto.ChatbotResponseRequest;
import com.fmt.fmt_backend.entity.ChatbotResponse;
import com.fmt.fmt_backend.repository.ChatbotResponseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/chatbot")
@RequiredArgsConstructor
@Tag(name = "Chatbot Admin", description = "Manage chatbot responses — ADMIN only")
public class ChatbotAdminController {

    private final ChatbotResponseRepository chatbotResponseRepository;

    @GetMapping("/responses")
    @Operation(summary = "List all chatbot responses")
    public ResponseEntity<ApiResponse<List<ChatbotResponseDto>>> getResponses() {
        List<ChatbotResponseDto> list = chatbotResponseRepository.findAllByOrderByStateAscTriggerTypeAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Chatbot responses fetched", list));
    }

    @PostMapping("/responses")
    @Operation(summary = "Create a chatbot response")
    public ResponseEntity<ApiResponse<ChatbotResponseDto>> createResponse(
            @Valid @RequestBody ChatbotResponseRequest request) {
        ChatbotResponse entity = fromRequest(new ChatbotResponse(), request);
        entity = chatbotResponseRepository.save(entity);
        return ResponseEntity.status(201).body(ApiResponse.success("Response created", toDto(entity)));
    }

    @PutMapping("/responses/{id}")
    @Operation(summary = "Update a chatbot response")
    public ResponseEntity<ApiResponse<ChatbotResponseDto>> updateResponse(
            @PathVariable UUID id,
            @Valid @RequestBody ChatbotResponseRequest request) {
        ChatbotResponse entity = chatbotResponseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatbot response not found"));
        entity = fromRequest(entity, request);
        entity = chatbotResponseRepository.save(entity);
        return ResponseEntity.ok(ApiResponse.success("Response updated", toDto(entity)));
    }

    @DeleteMapping("/responses/{id}")
    @Operation(summary = "Delete a chatbot response")
    public ResponseEntity<ApiResponse<Void>> deleteResponse(@PathVariable UUID id) {
        if (!chatbotResponseRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chatbot response not found");
        }
        chatbotResponseRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Response deleted", null));
    }

    private ChatbotResponse fromRequest(ChatbotResponse entity, ChatbotResponseRequest r) {
        entity.setState(r.getState());
        entity.setTriggerType(r.getTriggerType());
        entity.setTriggerValue(r.getTriggerValue());
        entity.setMessageType(r.getMessageType());
        entity.setResponseText(r.getResponseText());
        entity.setButtonsJson(r.getButtonsJson());
        entity.setNextState(r.getNextState());
        entity.setUpdatesLeadStatus(r.getUpdatesLeadStatus());
        entity.setIsActive(r.getIsActive() != null ? r.getIsActive() : true);
        return entity;
    }

    private ChatbotResponseDto toDto(ChatbotResponse r) {
        return ChatbotResponseDto.builder()
                .id(r.getId())
                .state(r.getState())
                .triggerType(r.getTriggerType())
                .triggerValue(r.getTriggerValue())
                .messageType(r.getMessageType())
                .responseText(r.getResponseText())
                .buttonsJson(r.getButtonsJson())
                .nextState(r.getNextState())
                .updatesLeadStatus(r.getUpdatesLeadStatus())
                .isActive(r.getIsActive())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
