package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Course;
import com.fmt.fmt_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByMentorOrderByCreatedAtDesc(User mentor);

    List<Course> findByIsActiveTrueOrderByCreatedAtDesc();

    long countByMentor(User mentor);
}
