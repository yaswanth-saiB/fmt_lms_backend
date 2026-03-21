package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Course;
import com.fmt.fmt_backend.enums.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {

    List<Batch> findByCourseOrderByCreatedAtDesc(Course course);

    @Query("SELECT b FROM Batch b WHERE b.course.mentor.id = :mentorId ORDER BY b.createdAt DESC")
    List<Batch> findByMentorId(@Param("mentorId") UUID mentorId);

    @Query("SELECT COUNT(b) FROM Batch b WHERE b.course.mentor.id = :mentorId")
    long countByMentorId(@Param("mentorId") UUID mentorId);

    List<Batch> findByStatus(BatchStatus status);
}
