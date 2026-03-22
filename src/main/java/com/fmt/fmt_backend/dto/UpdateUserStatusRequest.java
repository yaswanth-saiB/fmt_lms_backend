package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {

    @NotNull(message = "isActive is required")
    private Boolean isActive;
}
