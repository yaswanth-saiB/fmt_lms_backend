package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.CourseDTO;
import com.fmt.fmt_backend.dto.CreateCourseRequest;
import com.fmt.fmt_backend.entity.Course;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.UserRole;
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
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Transactional
    public CourseDTO createCourse(UUID mentorId, CreateCourseRequest request) {
        log.info("Creating course for mentor: {}", mentorId);

        User mentor = userRepository.findById(mentorId)
                .orElseThrow(() -> new RuntimeException("Mentor not found"));

        // Verify user is actually a mentor
        if (mentor.getUserRole() != UserRole.MENTOR) {
            throw new RuntimeException("User is not a mentor");
        }

        Course course = Course.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .mentor(mentor)
                .price(request.getPrice())
                .durationHours(request.getDurationHours())
                .status(Course.CourseStatus.DRAFT)
                .createdBy(mentorId)
                .build();

        Course savedCourse = courseRepository.save(course);
        log.info("✅ Course created with ID: {}", savedCourse.getId());

        return CourseDTO.fromEntity(savedCourse);
    }

    @Transactional(readOnly = true)
    public List<CourseDTO> getAllPublishedCourses() {
        log.info("Fetching all published courses");

        return courseRepository.findAllPublished().stream()
                .map(CourseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseDTO> getMentorCourses(UUID mentorId) {
        log.info("Fetching courses for mentor: {}", mentorId);

        return courseRepository.findByMentorId(mentorId).stream()
                .map(CourseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CourseDTO> getStudentCourses(UUID studentId) {
        log.info("Fetching courses for student: {}", studentId);

        return courseRepository.findCoursesByStudentId(studentId).stream()
                .map(CourseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CourseDTO getCourseById(UUID courseId) {
        log.info("Fetching course by ID: {}", courseId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        return CourseDTO.fromEntity(course);
    }

    @Transactional
    public CourseDTO updateCourse(UUID courseId, UUID mentorId, CreateCourseRequest request) {
        log.info("Updating course: {} by mentor: {}", courseId, mentorId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // Verify mentor owns this course
        if (!course.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to update this course");
        }

        // Only allow updates if course is in DRAFT
        if (course.getStatus() != Course.CourseStatus.DRAFT) {
            throw new RuntimeException("Only draft courses can be updated");
        }

        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setPrice(request.getPrice());
        course.setDurationHours(request.getDurationHours());

        Course updatedCourse = courseRepository.save(course);
        log.info("✅ Course updated: {}", courseId);

        return CourseDTO.fromEntity(updatedCourse);
    }

    @Transactional
    public CourseDTO publishCourse(UUID courseId, UUID mentorId) {
        log.info("Publishing course: {} by mentor: {}", courseId, mentorId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to publish this course");
        }

        course.setStatus(Course.CourseStatus.PUBLISHED);
        Course publishedCourse = courseRepository.save(course);
        log.info("✅ Course published: {}", courseId);

        return CourseDTO.fromEntity(publishedCourse);
    }

    @Transactional
    public CourseDTO archiveCourse(UUID courseId, UUID mentorId) {
        log.info("Archiving course: {} by mentor: {}", courseId, mentorId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        if (!course.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to archive this course");
        }

        course.setStatus(Course.CourseStatus.ARCHIVED);
        Course archivedCourse = courseRepository.save(course);
        log.info("✅ Course archived: {}", courseId);

        return CourseDTO.fromEntity(archivedCourse);
    }
}