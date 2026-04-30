package com.fmt.fmt_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LeadNoteRequest {

    @NotBlank(message = "Note cannot be blank")
    private String note;
}
