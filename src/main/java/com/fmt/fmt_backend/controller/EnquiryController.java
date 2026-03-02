package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.EnquiryRequest;
import com.fmt.fmt_backend.dto.EnquiryResponse;
import com.fmt.fmt_backend.service.EnquiryService;
import com.fmt.fmt_backend.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enquiry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Enquiry", description = "Public enquiry form API - No authentication required")
public class EnquiryController {

    private final EnquiryService enquiryService;

    @PostMapping("/submit")
    @Operation(
            summary = "Submit enquiry form",
            description = "Public endpoint for website visitors to submit their details. Name and mobile are mandatory."
    )
    @SecurityRequirements({})  // Explicitly mark as public (no token needed)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Enquiry submitted successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Thank you for your enquiry! Our team will contact you soon.\",\"data\":{...}}")
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation error - missing or invalid fields",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"success\":false,\"message\":\"Mobile number is required\"}")
                    )
            )
    })
    public ResponseEntity<ApiResponse<EnquiryResponse>> submitEnquiry(
            @Valid @RequestBody EnquiryRequest enquiryRequest) {

        log.info("📋 Enquiry submission received from: {}", enquiryRequest.getName());

        EnquiryResponse response = enquiryService.submitEnquiry(enquiryRequest);

        return ResponseEntity.ok(ApiResponse.success(
                "Thank you for your enquiry! Our team will contact you soon.",
                response
        ));
    }
}