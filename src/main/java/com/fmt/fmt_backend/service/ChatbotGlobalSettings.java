package com.fmt.fmt_backend.service;

import org.springframework.stereotype.Component;

/**
 * In-memory global on/off switch for the chatbot.
 * Toggled via admin API — resets to true on server restart (which is fine).
 */
@Component
public class ChatbotGlobalSettings {

    private volatile boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
