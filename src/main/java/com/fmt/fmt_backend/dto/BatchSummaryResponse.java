package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class BatchSummaryResponse {
    private UUID id;
    private String name;
}
