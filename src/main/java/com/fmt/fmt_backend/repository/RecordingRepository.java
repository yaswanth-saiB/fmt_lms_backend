package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Recording;
import com.fmt.fmt_backend.enums.RecordingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecordingRepository extends JpaRepository<Recording, UUID> {

    List<Recording> findByBatchAndStatusOrderByCreatedAtDesc(Batch batch, RecordingStatus status);

    @Query("SELECT r FROM Recording r WHERE r.batch.id IN " +
           "(SELECT e.batch.id FROM BatchEnrollment e WHERE e.student.id = :studentId AND e.isActive = true) " +
           "AND r.status = 'AVAILABLE' ORDER BY r.createdAt DESC")
    List<Recording> findAvailableRecordingsForStudent(@Param("studentId") UUID studentId);

    @Query("SELECT r FROM Recording r WHERE r.batch.course.mentor.id = :mentorId ORDER BY r.createdAt DESC")
    List<Recording> findByMentorId(@Param("mentorId") UUID mentorId);

    List<Recording> findByStatusAndExpiresAtBefore(RecordingStatus status, LocalDateTime now);
}
