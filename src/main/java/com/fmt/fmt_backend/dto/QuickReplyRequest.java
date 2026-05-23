package com.fmt.fmt_backend.dto;

import lombok.Data;

@Data
public class QuickReplyRequest {
    private String title;
    private String content;
}
