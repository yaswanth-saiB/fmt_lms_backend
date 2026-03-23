package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.CourseRequest;
import com.fmt.fmt_backend.dto.CourseResponse;
import com.fmt.fmt_backend.entity.Course;
import com.fmt.fmt_backend.entity.User;
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
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseResponse createCourse(CourseRequest request, UUID mentorId) {
        User mentor = userRepository.findById(mentorId)
                .orElseThrow(() -> new RuntimeException("Mentor not found"));

        Course course = Course.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .price(request.getPrice())
                .mentor(mentor)
                .build();

        Course saved = courseRepository.save(course);
        log.info("Course created: {} by mentor {}", saved.getId(), mentorId);
        return toResponse(saved);
    }

    public List<CourseResponse> getMentorCourses(UUID mentorId) {
        User mentor = userRepository.findById(mentorId)
                .orElseThrow(() -> new RuntimeException("Mentor not found"));
        return courseRepository.findByMentorOrderByCreatedAtDesc(mentor)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<CourseResponse> getAllActiveCourses() {
        return courseRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public CourseResponse getCourse(UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        return toResponse(course);
    }

    public CourseResponse updateCourse(UUID courseId, CourseRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            course.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            course.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            course.setPrice(request.getPrice());
        }
        Course saved = courseRepository.save(course);
        log.info("Course updated: {}", courseId);
        return toResponse(saved);
    }

    public void toggleCourseActive(UUID courseId, boolean isActive) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));
        course.setIsActive(isActive);
        courseRepository.save(course);
        log.info("Course {} isActive set to {}", courseId, isActive);
    }

    public CourseResponse toResponse(Course c) {
        return CourseResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .description(c.getDescription())
                .price(c.getPrice())
                .isActive(c.getIsActive())
                .mentorName(c.getMentor().getFirstName() + " " + c.getMentor().getLastName())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
