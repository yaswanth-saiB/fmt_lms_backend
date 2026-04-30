package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LeadImportResponse {

    private int imported;
    private int skipped;
    private List<String> errors;
}
