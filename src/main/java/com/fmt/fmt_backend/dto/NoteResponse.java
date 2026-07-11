package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NoteResponse {
    private UUID id;
    private String content;
    private String createdByName;
    private LocalDateTime createdAt;
    private String source;   // WA_INBOX | LEAD_PAGE
    private boolean deletable;
}
