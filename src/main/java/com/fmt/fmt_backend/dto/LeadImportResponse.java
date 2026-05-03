package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LeadImportResponse {

    private int imported;
    private int skipped;       // total skipped = duplicates + blankPhone
    private int duplicates;    // phone already exists in CRM
    private int blankPhone;    // row had no phone number
    private List<String> errors;
}
