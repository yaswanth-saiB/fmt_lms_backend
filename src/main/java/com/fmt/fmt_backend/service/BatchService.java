package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.BatchRequest;
import com.fmt.fmt_backend.dto.BatchResponse;
import com.fmt.fmt_backend.dto.EnrollmentRequest;
import com.fmt.fmt_backend.dto.StudentSummaryResponse;
import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.BatchEnrollment;
import com.fmt.fmt_backend.entity.Course;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.BatchStatus;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.repository.BatchEnrollmentRepository;
import com.fmt.fmt_backend.repository.BatchRepository;
import com.fmt.fmt_backend.repository.CourseRepository;
import com.fmt.fmt_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchService {

    private final BatchRepository batchRepository;
    private final BatchEnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public BatchResponse createBatch(BatchRequest request, UUID mentorId) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You can only create batches for your own courses");
        }

        Batch batch = Batch.builder()
                .name(request.getName())
                .course(course)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .maxStudents(request.getMaxStudents())
                .description(request.getDescription())
                .build();

        Batch saved = batchRepository.save(batch);
        log.info("Batch created: {} for course {}", saved.getId(), course.getId());
        return toResponse(saved);
    }

    public List<BatchResponse> getMentorBatches(UUID mentorId) {
        return batchRepository.findByMentorId(mentorId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public BatchResponse getBatch(UUID batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));
        return toResponse(batch);
    }

    public List<BatchResponse> getBatchesForCourse(UUID courseId, UUID mentorId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("Access denied");
        }

        return batchRepository.findByCourseOrderByCreatedAtDesc(course)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional
    public void enrollStudent(EnrollmentRequest request, UUID requestingMentorId) {
        Batch batch = batchRepository.findById(request.getBatchId())
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        if (!batch.getCourse().getMentor().getId().equals(requestingMentorId)) {
            throw new RuntimeException("You can only enroll students in your own batches");
        }

        User student = userRepository.findById(request.getStudentId())
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (student.getUserRole() != UserRole.STUDENT) {
            throw new RuntimeException("User is not a student");
        }

        if (enrollmentRepository.existsByBatchAndStudentAndIsActiveTrue(batch, student)) {
            throw new RuntimeException("Student is already enrolled in this batch");
        }

        long currentCount = enrollmentRepository.countByBatchAndIsActiveTrue(batch);
        if (batch.getMaxStudents() != null && currentCount >= batch.getMaxStudents()) {
            throw new RuntimeException("Batch is full");
        }

        BatchEnrollment enrollment = BatchEnrollment.builder()
                .batch(batch)
                .student(student)
                .build();

        enrollmentRepository.save(enrollment);
        log.info("Student {} enrolled in batch {}", student.getId(), batch.getId());
    }

    public List<BatchResponse> getStudentEnrolledBatches(UUID studentId) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        return enrollmentRepository.findByStudentAndIsActiveTrueOrderByEnrolledAtDesc(student)
                .stream()
                .map(e -> toResponse(e.getBatch()))
                .collect(Collectors.toList());
    }

    public List<StudentSummaryResponse> getBatchStudents(UUID batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));
        return enrollmentRepository.findByBatchAndIsActiveTrue(batch)
                .stream()
                .map(e -> StudentSummaryResponse.builder()
                        .id(e.getStudent().getId())
                        .firstName(e.getStudent().getFirstName())
                        .lastName(e.getStudent().getLastName())
                        .email(e.getStudent().getEmail())
                        .phoneNumber(e.getStudent().getPhoneNumber())
                        .enrolledAt(e.getEnrolledAt())
                        .build())
                .collect(Collectors.toList());
    }

    public List<StudentSummaryResponse> searchStudents(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return userRepository.searchByRoleAndQuery(UserRole.STUDENT, query.trim())
                .stream()
                .map(u -> StudentSummaryResponse.builder()
                        .id(u.getId())
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .email(u.getEmail())
                        .phoneNumber(u.getPhoneNumber())
                        .build())
                .collect(Collectors.toList());
    }

    public void unenrollStudent(UUID batchId, UUID studentId, UUID requestingMentorId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        if (!batch.getCourse().getMentor().getId().equals(requestingMentorId)) {
            throw new RuntimeException("You can only manage students in your own batches");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        BatchEnrollment enrollment = enrollmentRepository.findByBatchAndStudent(batch, student)
                .orElseThrow(() -> new RuntimeException("Student is not enrolled in this batch"));

        enrollment.setIsActive(false);
        enrollmentRepository.save(enrollment);
        log.info("Student {} unenrolled from batch {}", studentId, batchId);
    }

    public BatchResponse updateBatchStatus(UUID batchId, BatchStatus newStatus, UUID requestingMentorId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        if (!batch.getCourse().getMentor().getId().equals(requestingMentorId)) {
            throw new RuntimeException("You can only update your own batches");
        }

        batch.setStatus(newStatus);
        Batch saved = batchRepository.save(batch);
        log.info("Batch {} status updated to {}", batchId, newStatus);
        return toResponse(saved);
    }

    public boolean isStudentEnrolled(UUID batchId, UUID studentId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        return enrollmentRepository.existsByBatchAndStudentAndIsActiveTrue(batch, student);
    }

    public BatchResponse toResponse(Batch b) {
        long enrolled = enrollmentRepository.countByBatchAndIsActiveTrue(b);
        return BatchResponse.builder()
                .id(b.getId())
                .name(b.getName())
                .courseId(b.getCourse().getId())
                .courseName(b.getCourse().getTitle())
                .startDate(b.getStartDate())
                .endDate(b.getEndDate())
                .maxStudents(b.getMaxStudents())
                .enrolledCount(enrolled)
                .status(b.getStatus())
                .description(b.getDescription())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
