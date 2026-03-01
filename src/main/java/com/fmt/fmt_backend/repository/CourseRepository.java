package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByMentorId(UUID mentorId);

    @Query("SELECT c FROM Course c WHERE c.status = 'PUBLISHED'")
    List<Course> findAllPublished();

    @Query("SELECT c FROM Course c JOIN c.batches b JOIN b.enrollments e WHERE e.student.id = :studentId")
    List<Course> findCoursesByStudentId(@Param("studentId") UUID studentId);

    boolean existsByTitleAndMentorId(String title, UUID mentorId);
}