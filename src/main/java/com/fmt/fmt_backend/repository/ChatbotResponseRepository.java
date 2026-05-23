package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.ChatbotResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatbotResponseRepository extends JpaRepository<ChatbotResponse, UUID> {

    List<ChatbotResponse> findByIsActiveTrueOrderByStateAscTriggerTypeAsc();

    // Exact match: state + BUTTON_ID trigger
    Optional<ChatbotResponse> findByStateAndTriggerTypeAndTriggerValueAndIsActiveTrue(
            String state, String triggerType, String triggerValue);

    // Wildcard state (*) + BUTTON_ID trigger
    Optional<ChatbotResponse> findByStateAndTriggerTypeAndTriggerValueAndIsActiveTrueAndState(
            String state, String triggerType, String triggerValue, String wildcardState);

    // DEFAULT fallback for a state
    Optional<ChatbotResponse> findByStateAndTriggerTypeAndIsActiveTrue(String state, String triggerType);

    List<ChatbotResponse> findAllByOrderByStateAscTriggerTypeAsc();
}
