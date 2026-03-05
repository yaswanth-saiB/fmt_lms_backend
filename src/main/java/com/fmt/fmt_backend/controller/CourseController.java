package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.CourseDTO;
import com.fmt.fmt_backend.dto.CreateCourseRequest;
import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Courses", description = "Course management APIs for mentors and students")
public class CourseController {

    private final CourseService courseService;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Create a new course",
            description = "Mentor can create a new course. Course will be in DRAFT status initially.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Course created successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a mentor")
    })
    public ResponseEntity<ApiResponse<CourseDTO>> createCourse(
            @Valid @RequestBody CreateCourseRequest request) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        CourseDTO course = courseService.createCourse(currentUser.getId(), request);

        return ResponseEntity.ok(ApiResponse.success("Course created successfully", course));
    }

    @GetMapping
    @Operation(
            summary = "Get all published courses",
            description = "Public endpoint to browse all published courses. No authentication required."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Courses retrieved successfully")
    })
    public ResponseEntity<ApiResponse<List<CourseDTO>>> getAllPublishedCourses() {
        List<CourseDTO> courses = courseService.getAllPublishedCourses();
        return ResponseEntity.ok(ApiResponse.success("Courses retrieved", courses));
    }

    @GetMapping("/mentor")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Get mentor's courses",
            description = "Get all courses created by the current mentor (including drafts)",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<List<CourseDTO>>> getMentorCourses() {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<CourseDTO> courses = courseService.getMentorCourses(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Your courses retrieved", courses));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(
            summary = "Get student's enrolled courses",
            description = "Get all courses the current student is enrolled in",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<List<CourseDTO>>> getStudentCourses() {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<CourseDTO> courses = courseService.getStudentCourses(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Your enrolled courses retrieved", courses));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get course details by ID",
            description = "Get detailed information about a specific course"
    )
    public ResponseEntity<ApiResponse<CourseDTO>> getCourseById(
            @Parameter(description = "Course ID", required = true)
            @PathVariable UUID id) {

        CourseDTO course = courseService.getCourseById(id);
        return ResponseEntity.ok(ApiResponse.success("Course details retrieved", course));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Update course",
            description = "Mentor can update their own course (only in DRAFT status)",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<CourseDTO>> updateCourse(
            @Parameter(description = "Course ID", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody CreateCourseRequest request) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        CourseDTO course = courseService.updateCourse(id, currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Course updated successfully", course));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Publish course",
            description = "Change course status from DRAFT to PUBLISHED",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<CourseDTO>> publishCourse(
            @Parameter(description = "Course ID", required = true)
            @PathVariable UUID id) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        CourseDTO course = courseService.publishCourse(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Course published successfully", course));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(
            summary = "Archive course",
            description = "Change course status to ARCHIVED",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<CourseDTO>> archiveCourse(
            @Parameter(description = "Course ID", required = true)
            @PathVariable UUID id) {

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        CourseDTO course = courseService.archiveCourse(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Course archived successfully", course));
    }
}