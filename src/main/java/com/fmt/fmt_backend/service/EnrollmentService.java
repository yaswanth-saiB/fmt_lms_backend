package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.EnrollmentDTO;
import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Enrollment;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.repository.BatchRepository;
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
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final BatchRepository batchRepository;
    private final UserRepository userRepository;

    @Transactional
    public EnrollmentDTO enroll(UUID studentId, UUID batchId) {
        log.info("Enrolling student: {} in batch: {}", studentId, batchId);

        // Check if student exists
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (student.getUserRole() != UserRole.STUDENT) {
            throw new RuntimeException("User is not a student");
        }

        // Check if batch exists
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        // Check if already enrolled
        if (enrollmentRepository.existsByStudentIdAndBatchId(studentId, batchId)) {
            throw new RuntimeException("Already enrolled in this batch");
        }

        // Check batch capacity
        long currentEnrollments = enrollmentRepository.countActiveEnrollmentsByBatchId(batchId);
        if (currentEnrollments >= batch.getMaxStudents()) {
            throw new RuntimeException("Batch is full");
        }

        // Create enrollment
        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .batch(batch)
                .status(Enrollment.EnrollmentStatus.ACTIVE)
                .progressPercentage(0)
                .build();

        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);

        // Update batch current students count
        batch.setCurrentStudents(batch.getCurrentStudents() + 1);
        batchRepository.save(batch);

        log.info("✅ Student enrolled successfully. Enrollment ID: {}", savedEnrollment.getId());

        return EnrollmentDTO.fromEntity(savedEnrollment);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDTO> getStudentEnrollments(UUID studentId) {
        log.info("Fetching enrollments for student: {}", studentId);

        return enrollmentRepository.findByStudentId(studentId).stream()
                .map(EnrollmentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDTO> getBatchEnrollments(UUID batchId, UUID mentorId) {
        log.info("Fetching enrollments for batch: {} by mentor: {}", batchId, mentorId);

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        // Verify mentor owns this batch
        if (!batch.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to view these enrollments");
        }

        return enrollmentRepository.findByBatchId(batchId).stream()
                .map(EnrollmentDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public EnrollmentDTO updateProgress(UUID enrollmentId, UUID mentorId, Integer progress) {
        log.info("Updating progress for enrollment: {} to {}%", enrollmentId, progress);

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        // Verify mentor owns this batch
        if (!enrollment.getBatch().getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to update this enrollment");
        }

        if (progress < 0 || progress > 100) {
            throw new RuntimeException("Progress must be between 0 and 100");
        }

        enrollment.setProgressPercentage(progress);

        // If progress is 100%, mark as completed
        if (progress == 100) {
            enrollment.setStatus(Enrollment.EnrollmentStatus.COMPLETED);
        }

        Enrollment updatedEnrollment = enrollmentRepository.save(enrollment);
        log.info("✅ Progress updated for enrollment: {}", enrollmentId);

        return EnrollmentDTO.fromEntity(updatedEnrollment);
    }

    @Transactional
    public EnrollmentDTO dropFromBatch(UUID enrollmentId, UUID studentId) {
        log.info("Student: {} dropping from enrollment: {}", studentId, enrollmentId);

        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found"));

        // Verify student owns this enrollment
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new RuntimeException("You don't have permission to drop this enrollment");
        }

        enrollment.setStatus(Enrollment.EnrollmentStatus.DROPPED);

        // Update batch current students count
        Batch batch = enrollment.getBatch();
        batch.setCurrentStudents(batch.getCurrentStudents() - 1);
        batchRepository.save(batch);

        Enrollment droppedEnrollment = enrollmentRepository.save(enrollment);
        log.info("✅ Student dropped from batch");

        return EnrollmentDTO.fromEntity(droppedEnrollment);
    }
}