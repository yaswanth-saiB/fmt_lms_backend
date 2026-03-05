package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.BatchDTO;
import com.fmt.fmt_backend.dto.BatchResponse;
import com.fmt.fmt_backend.dto.CreateBatchRequest;
import com.fmt.fmt_backend.dto.StudentDTO;
import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Course;
import com.fmt.fmt_backend.entity.Enrollment;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.repository.BatchRepository;
import com.fmt.fmt_backend.repository.CourseRepository;
import com.fmt.fmt_backend.repository.EnrollmentRepository;
import com.fmt.fmt_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchService {

    private final BatchRepository batchRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional
    public BatchDTO createBatch(UUID mentorId, CreateBatchRequest request) {
        log.info("Creating batch for course: {} by mentor: {}", request.getCourseId(), mentorId);

        User mentor = userRepository.findById(mentorId)
                .orElseThrow(() -> new RuntimeException("Mentor not found"));

        if (mentor.getUserRole() != UserRole.MENTOR) {
            throw new RuntimeException("User is not a mentor");
        }

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Verify mentor owns this course
        if (!course.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to create batches for this course");
        }

        // Validate dates
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new RuntimeException("End date must be after start date");
        }

        Batch batch = Batch.builder()
                .name(request.getName())
                .course(course)
                .mentor(mentor)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .maxStudents(request.getMaxStudents())
                .currentStudents(0)
                .status(Batch.BatchStatus.UPCOMING)
                .build();

        Batch savedBatch = batchRepository.save(batch);
        log.info("✅ Batch created with ID: {}", savedBatch.getId());

        return BatchDTO.fromEntity(savedBatch);
    }

    @Transactional(readOnly = true)
    public List<BatchDTO> getMentorBatches(UUID mentorId) {
        log.info("Fetching batches for mentor: {}", mentorId);

        return batchRepository.findByMentorId(mentorId).stream()
                .map(BatchDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchDTO> getStudentBatches(UUID studentId) {
        log.info("Fetching batches for student: {}", studentId);

        return batchRepository.findByStudentId(studentId).stream()
                .map(BatchDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BatchDTO getBatchById(UUID batchId) {
        log.info("Fetching batch by ID: {}", batchId);

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        return BatchDTO.fromEntity(batch);
    }

    @Transactional(readOnly = true)
    public List<StudentDTO> getBatchStudents(UUID batchId, UUID mentorId) {
        log.info("Fetching students for batch: {} by mentor: {}", batchId, mentorId);

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        // Verify mentor owns this batch
        if (!batch.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to view students in this batch");
        }

        List<Enrollment> enrollments = enrollmentRepository.findByBatchId(batchId);

        return enrollments.stream()
                .map(enrollment -> StudentDTO.builder()
                        .id(enrollment.getStudent().getId())
                        .firstName(enrollment.getStudent().getFirstName())
                        .lastName(enrollment.getStudent().getLastName())
                        .email(enrollment.getStudent().getEmail())
                        .phoneNumber(enrollment.getStudent().getPhoneNumber())
                        .enrolledAt(enrollment.getEnrolledAt())
                        .progress(enrollment.getProgressPercentage())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public BatchDTO updateBatchStatus(UUID batchId, UUID mentorId, String status) {
        log.info("Updating batch status: {} to {} by mentor: {}", batchId, status, mentorId);

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        if (!batch.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to update this batch");
        }

        try {
            Batch.BatchStatus newStatus = Batch.BatchStatus.valueOf(status.toUpperCase());
            batch.setStatus(newStatus);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid status. Allowed values: UPCOMING, ONGOING, COMPLETED, CANCELLED");
        }

        Batch updatedBatch = batchRepository.save(batch);
        log.info("✅ Batch status updated to: {}", status);

        return BatchDTO.fromEntity(updatedBatch);
    }
}