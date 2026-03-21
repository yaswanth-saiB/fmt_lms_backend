package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.BatchStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BatchStatusRequest {

    @NotNull(message = "Status is required")
    private BatchStatus status;
}
