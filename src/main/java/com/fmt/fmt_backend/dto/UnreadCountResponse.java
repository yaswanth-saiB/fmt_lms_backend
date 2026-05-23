package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UnreadCountResponse {
    private long total;
    private long needsHuman;
}
