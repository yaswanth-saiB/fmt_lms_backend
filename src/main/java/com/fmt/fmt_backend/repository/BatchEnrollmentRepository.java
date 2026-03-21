package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.BatchEnrollment;
import com.fmt.fmt_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchEnrollmentRepository extends JpaRepository<BatchEnrollment, UUID> {

    List<BatchEnrollment> findByStudentAndIsActiveTrueOrderByEnrolledAtDesc(User student);

    List<BatchEnrollment> findByBatchAndIsActiveTrue(Batch batch);

    Optional<BatchEnrollment> findByBatchAndStudent(Batch batch, User student);

    boolean existsByBatchAndStudentAndIsActiveTrue(Batch batch, User student);

    long countByBatchAndIsActiveTrue(Batch batch);

    @Query("SELECT COUNT(DISTINCT e.student.id) FROM BatchEnrollment e WHERE e.batch.course.mentor.id = :mentorId AND e.isActive = true")
    long countTotalStudentsByMentorId(@Param("mentorId") UUID mentorId);
}
