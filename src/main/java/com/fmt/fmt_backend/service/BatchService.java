package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.BatchResponse;
import com.fmt.fmt_backend.dto.CreateBatchRequest;
import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Course;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.repository.BatchRepository;
import com.fmt.fmt_backend.repository.CourseRepository;
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

    @Transactional
    public BatchResponse createBatch(UUID mentorId, CreateBatchRequest request) {
        log.info("Creating batch for course: {} by mentor: {}", request.getCourseId(), mentorId);

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Verify mentor owns this course
        if (!course.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to create batches for this course");
        }

        User mentor = userRepository.findById(mentorId).get();

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

        return mapToResponse(savedBatch);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getMentorBatches(UUID mentorId) {
        return batchRepository.findByMentorId(mentorId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getStudentBatches(UUID studentId) {
        return batchRepository.findByStudentId(studentId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private BatchResponse mapToResponse(Batch batch) {
        return BatchResponse.builder()
                .id(batch.getId())
                .name(batch.getName())
                .courseId(batch.getCourse().getId())
                .courseName(batch.getCourse().getTitle())
                .mentorId(batch.getMentor().getId())
                .mentorName(batch.getMentor().getFirstName() + " " + batch.getMentor().getLastName())
                .startDate(batch.getStartDate())
                .endDate(batch.getEndDate())
                .maxStudents(batch.getMaxStudents())
                .currentStudents(batch.getCurrentStudents())
                .status(batch.getStatus().name())
                .meetingCount(batch.getMeetings() != null ? batch.getMeetings().size() : 0)
                .build();
    }
}
