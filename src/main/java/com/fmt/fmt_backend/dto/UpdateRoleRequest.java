package com.fmt.fmt_backend.dto;

import com.fmt.fmt_backend.enums.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRoleRequest {

    @NotNull(message = "Role is required")
    private UserRole role;
}
