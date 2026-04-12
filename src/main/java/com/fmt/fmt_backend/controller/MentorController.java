package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.dto.RescheduleMeetingRequest;
import com.fmt.fmt_backend.entity.Enquiry;
import com.fmt.fmt_backend.enums.BatchStatus;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.repository.BatchEnrollmentRepository;
import com.fmt.fmt_backend.repository.BatchRepository;
import com.fmt.fmt_backend.repository.CourseRepository;
import com.fmt.fmt_backend.repository.MeetingRepository;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.BatchService;
import com.fmt.fmt_backend.service.CourseService;
import com.fmt.fmt_backend.service.EnquiryService;
import com.fmt.fmt_backend.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mentor")
@RequiredArgsConstructor
@Tag(name = "Mentor", description = "Mentor dashboard, courses, batches, classes, and student management")
@SecurityRequirement(name = "bearerAuth")
public class MentorController {

    private final AuthService authService;
    private final CourseService courseService;
    private final BatchService batchService;
    private final MeetingService meetingService;
    private final CourseRepository courseRepository;
    private final BatchRepository batchRepository;
    private final BatchEnrollmentRepository enrollmentRepository;
    private final MeetingRepository meetingRepository;
    private final EnquiryService enquiryService;

    // ---------------------------------------------------------------
    // Dashboard
    // ---------------------------------------------------------------

    @GetMapping("/dashboard")
    @Operation(summary = "Mentor dashboard — total courses, batches, classes, students")
    public ResponseEntity<ApiResponse<MentorDashboardResponse>> dashboard() {
        User mentor = currentMentor();
        UUID mentorId = mentor.getId();

        long totalCourses = courseRepository.countByMentor(mentor);
        long totalBatches = batchRepository.countByMentorId(mentorId);
        long totalClasses = meetingRepository.countByMentorId(mentorId);
        long totalStudents = enrollmentRepository.countTotalStudentsByMentorId(mentorId);

        List<BatchResponse> recentBatches = batchService.getMentorBatches(mentorId)
                .stream().limit(5).toList();

        List<MeetingResponse> upcomingClasses = meetingService.getMentorMeetings(mentorId)
                .stream()
                .filter(m -> m.getStatus().name().equals("UPCOMING"))
                .limit(5).toList();

        MentorDashboardResponse dashboard = MentorDashboardResponse.builder()
                .totalCourses(totalCourses)
                .totalBatches(totalBatches)
                .totalClasses(totalClasses)
                .totalStudents(totalStudents)
                .recentBatches(recentBatches)
                .upcomingClasses(upcomingClasses)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Dashboard loaded", dashboard));
    }

    // ---------------------------------------------------------------
    // Courses
    // ---------------------------------------------------------------

    @GetMapping("/courses")
    @Operation(summary = "List all courses — any mentor can view all courses")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getCourses() {
        List<CourseResponse> courses = courseService.getAllActiveCourses();
        return ResponseEntity.ok(ApiResponse.success("Courses fetched", courses));
    }

    @PostMapping("/courses")
    @Operation(summary = "Create a new course")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @Valid @RequestBody CourseRequest request) {
        CourseResponse course = courseService.createCourse(request, currentMentor().getId());
        return ResponseEntity.ok(ApiResponse.success("Course created", course));
    }

    @GetMapping("/courses/{courseId}")
    @Operation(summary = "Get a course by ID")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourse(@PathVariable UUID courseId) {
        return ResponseEntity.ok(ApiResponse.success("Course fetched", courseService.getCourse(courseId)));
    }

    @GetMapping("/courses/{courseId}/batches")
    @Operation(summary = "Get all batches for a specific course — any mentor can view")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getCourseBatches(@PathVariable UUID courseId) {
        List<BatchResponse> batches = batchService.getBatchesForCourse(courseId);
        return ResponseEntity.ok(ApiResponse.success("Batches fetched", batches));
    }

    // ---------------------------------------------------------------
    // Batches
    // ---------------------------------------------------------------

    @GetMapping("/batches")
    @Operation(summary = "List all batches — any mentor can view all batches to select for a class")
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getBatches() {
        List<BatchResponse> batches = batchService.getAllBatches();
        return ResponseEntity.ok(ApiResponse.success("Batches fetched", batches));
    }

    @PostMapping("/batches")
    @Operation(summary = "Create a new batch")
    public ResponseEntity<ApiResponse<BatchResponse>> createBatch(
            @Valid @RequestBody BatchRequest request) {
        BatchResponse batch = batchService.createBatch(request, currentMentor().getId());
        return ResponseEntity.ok(ApiResponse.success("Batch created", batch));
    }

    @GetMapping("/batches/{batchId}")
    @Operation(summary = "Get a batch by ID")
    public ResponseEntity<ApiResponse<BatchResponse>> getBatch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(ApiResponse.success("Batch fetched", batchService.getBatch(batchId)));
    }

    // ---------------------------------------------------------------
    // Classes (Meetings)
    // ---------------------------------------------------------------

    @GetMapping("/classes")
    @Operation(summary = "List all my class sessions with Zoom links",
            description = "Optional filter: ?status=UPCOMING,LIVE,ENDED,CANCELLED (comma-separated). Omit for all.")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getClasses(
            @RequestParam(required = false) List<com.fmt.fmt_backend.enums.MeetingStatus> status) {
        List<MeetingResponse> meetings = meetingService.getMentorMeetings(currentMentor().getId(), status);
        return ResponseEntity.ok(ApiResponse.success("Classes fetched", meetings));
    }

    @PostMapping("/classes")
    @Operation(summary = "Create a class — generates Zoom meeting, returns start_url + join_url")
    public ResponseEntity<ApiResponse<MeetingResponse>> createClass(
            @Valid @RequestBody MeetingRequest request) {
        MeetingResponse meeting = meetingService.createMeeting(request, currentMentor().getId());
        return ResponseEntity.ok(ApiResponse.success("Class created with Zoom meeting", meeting));
    }

    @GetMapping("/batches/{batchId}/classes")
    @Operation(summary = "Get all classes for a specific batch",
            description = "Optional filter: ?status=UPCOMING,LIVE,ENDED,CANCELLED (comma-separated). Omit for all.")
    public ResponseEntity<ApiResponse<List<MeetingResponse>>> getBatchClasses(
            @PathVariable UUID batchId,
            @RequestParam(required = false) List<com.fmt.fmt_backend.enums.MeetingStatus> status) {
        List<MeetingResponse> meetings = meetingService.getBatchMeetingsForMentor(batchId, currentMentor().getId(), status);
        return ResponseEntity.ok(ApiResponse.success("Classes fetched", meetings));
    }

    @PutMapping("/classes/{classId}/start")
    @Operation(summary = "Mark class as LIVE — call this when you click the Zoom start link",
            description = "Transitions UPCOMING → LIVE. Only the conducting mentor can start their own class.")
    public ResponseEntity<ApiResponse<MeetingResponse>> startClass(@PathVariable UUID classId) {
        MeetingResponse meeting = meetingService.startMeeting(classId, currentMentor().getId());
        return ResponseEntity.ok(ApiResponse.success("Class is now LIVE", meeting));
    }

    @PutMapping("/classes/{classId}/end")
    @Operation(summary = "Mark class as ENDED — call this when the session finishes",
            description = "Transitions LIVE → ENDED. Only the conducting mentor can end their own class.")
    public ResponseEntity<ApiResponse<MeetingResponse>> endClass(@PathVariable UUID classId) {
        MeetingResponse meeting = meetingService.endMeeting(classId, currentMentor().getId());
        return ResponseEntity.ok(ApiResponse.success("Class marked as ENDED", meeting));
    }

    @DeleteMapping("/classes/{classId}")
    @Operation(summary = "Cancel a class — only allowed for UPCOMING classes",
            description = "Soft-cancels the class (sets CANCELLED). Cannot cancel a LIVE or ENDED class.")
    public ResponseEntity<ApiResponse<Void>> cancelClass(@PathVariable UUID classId) {
        meetingService.cancelMeeting(classId, currentMentor().getId());
        return ResponseEntity.ok(ApiResponse.success("Class cancelled", null));
    }

    @PutMapping("/classes/{classId}/reschedule")
    @Operation(summary = "Reschedule a class — only allowed for UPCOMING classes",
            description = "Updates scheduledAt, durationMins (optional), topic (optional). Validates no time conflict.")
    public ResponseEntity<ApiResponse<MeetingResponse>> rescheduleClass(
            @PathVariable UUID classId,
            @Valid @RequestBody RescheduleMeetingRequest request) {
        MeetingResponse meeting = meetingService.rescheduleMeeting(classId, request, currentMentor().getId());
        return ResponseEntity.ok(ApiResponse.success("Class rescheduled", meeting));
    }

    // ---------------------------------------------------------------
    // Students / Enrollments
    // ---------------------------------------------------------------

    @GetMapping("/students/search")
    @Operation(summary = "Search students by name or email (for enrollment autocomplete)")
    public ResponseEntity<ApiResponse<List<StudentSummaryResponse>>> searchStudents(
            @RequestParam(name = "q") String query) {
        List<StudentSummaryResponse> results = batchService.searchStudents(query);
        return ResponseEntity.ok(ApiResponse.success("Search results", results));
    }

    @PostMapping("/students/enroll")
    @Operation(summary = "Enroll a student in a batch")
    public ResponseEntity<ApiResponse<Void>> enrollStudent(
            @Valid @RequestBody EnrollmentRequest request) {
        batchService.enrollStudent(request, currentMentor().getId());
        return ResponseEntity.ok(ApiResponse.success("Student enrolled successfully", null));
    }

    @DeleteMapping("/batches/{batchId}/students/{studentId}")
    @Operation(summary = "Unenroll a student from a batch")
    public ResponseEntity<ApiResponse<Void>> unenrollStudent(
            @PathVariable UUID batchId,
            @PathVariable UUID studentId) {
        batchService.unenrollStudent(batchId, studentId, currentMentor().getId());
        return ResponseEntity.ok(ApiResponse.success("Student unenrolled successfully", null));
    }

    @GetMapping("/batches/{batchId}/students")
    @Operation(summary = "List all students enrolled in a batch")
    public ResponseEntity<ApiResponse<List<StudentSummaryResponse>>> getBatchStudents(@PathVariable UUID batchId) {
        List<StudentSummaryResponse> students = batchService.getBatchStudents(batchId);
        return ResponseEntity.ok(ApiResponse.success("Students fetched", students));
    }

    @PutMapping("/batches/{batchId}/status")
    @Operation(summary = "Update batch status (UPCOMING → ACTIVE → COMPLETED / CANCELLED)")
    public ResponseEntity<ApiResponse<BatchResponse>> updateBatchStatus(
            @PathVariable UUID batchId,
            @Valid @RequestBody BatchStatusRequest request) {
        BatchResponse batch = batchService.updateBatchStatus(batchId, request.getStatus(), currentMentor().getId());
        return ResponseEntity.ok(ApiResponse.success("Batch status updated", batch));
    }

    // ---------------------------------------------------------------
    // Enquiries
    // ---------------------------------------------------------------

    @GetMapping("/enquiries")
    @Operation(summary = "List all enquiries — optionally filter by status")
    public ResponseEntity<ApiResponse<List<EnquiryResponse>>> getEnquiries(
            @RequestParam(required = false) Enquiry.EnquiryStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Enquiries fetched", enquiryService.getAll(status)));
    }

    @GetMapping("/enquiries/{enquiryId}")
    @Operation(summary = "Get a single enquiry by ID")
    public ResponseEntity<ApiResponse<EnquiryResponse>> getEnquiry(@PathVariable UUID enquiryId) {
        return ResponseEntity.ok(ApiResponse.success("Enquiry fetched", enquiryService.getById(enquiryId)));
    }

    @PutMapping("/enquiries/{enquiryId}/status")
    @Operation(summary = "Update enquiry status — NEW / CONTACTED / CLOSED")
    public ResponseEntity<ApiResponse<EnquiryResponse>> updateEnquiryStatus(
            @PathVariable UUID enquiryId,
            @Valid @RequestBody UpdateEnquiryStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Enquiry status updated",
                enquiryService.updateStatus(enquiryId, request.getStatus())
        ));
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private User currentMentor() {
        return authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));
    }
}
