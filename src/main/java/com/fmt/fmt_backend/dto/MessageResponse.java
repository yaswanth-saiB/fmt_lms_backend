package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.MessageDirection;
import com.fmt.fmt_backend.enums.WaMessageStatus;
import com.fmt.fmt_backend.enums.WaMessageType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MessageResponse {
    private UUID id;
    private MessageDirection direction;
    private WaMessageType messageType;
    private String content;
    private String buttonTitle;
    private WaMessageStatus status;
    private Boolean isBotMessage;
    private String sentByName;
    private String mediaId;
    private LocalDateTime sentAt;
}
