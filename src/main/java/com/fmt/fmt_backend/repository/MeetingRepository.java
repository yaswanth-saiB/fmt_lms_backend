package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Meeting;
import com.fmt.fmt_backend.enums.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    // All meetings that include this batch (many-to-many join)
    @Query("SELECT m FROM Meeting m JOIN m.batches b WHERE b = :batch ORDER BY m.createdAt DESC")
    List<Meeting> findByBatch(@Param("batch") Batch batch);

    @Query("SELECT m FROM Meeting m JOIN m.batches b WHERE b = :batch AND m.status = :status ORDER BY m.scheduledAt ASC")
    List<Meeting> findByBatchAndStatus(@Param("batch") Batch batch, @Param("status") MeetingStatus status);

    Optional<Meeting> findByZoomMeetingId(String zoomMeetingId);

    // Returns UPCOMING + LIVE for all batches the student is enrolled in (multi-batch aware)
    @Query("SELECT DISTINCT m FROM Meeting m JOIN m.batches b WHERE b.id IN " +
           "(SELECT e.batch.id FROM BatchEnrollment e WHERE e.student.id = :studentId AND e.isActive = true) " +
           "AND m.status IN ('UPCOMING', 'LIVE') ORDER BY m.scheduledAt ASC")
    List<Meeting> findUpcomingMeetingsForStudent(@Param("studentId") UUID studentId);

    @Query("SELECT m FROM Meeting m WHERE m.mentor.id = :mentorId ORDER BY m.createdAt DESC")
    List<Meeting> findByMentorId(@Param("mentorId") UUID mentorId);

    @Query("SELECT COUNT(m) FROM Meeting m WHERE m.mentor.id = :mentorId")
    long countByMentorId(@Param("mentorId") UUID mentorId);

    // Count meetings that include this batch
    @Query("SELECT COUNT(m) FROM Meeting m JOIN m.batches b WHERE b = :batch")
    long countByBatch(@Param("batch") Batch batch);

    // Admin — all upcoming classes across all batches
    @Query("SELECT m FROM Meeting m WHERE m.status = 'UPCOMING' ORDER BY m.scheduledAt ASC")
    List<Meeting> findAllUpcoming(org.springframework.data.domain.Pageable pageable);

    // Time conflict check — all non-finished meetings for a batch
    @Query("SELECT m FROM Meeting m JOIN m.batches b WHERE b = :batch AND m.status NOT IN ('ENDED', 'CANCELLED')")
    List<Meeting> findActiveOrUpcomingByBatch(@Param("batch") Batch batch);

    // Auto-end job — mark expired meetings as ENDED in one native update
    @Modifying
    @Query(value = """
        UPDATE meetings
        SET status = 'ENDED', ended_at = NOW()
        WHERE status IN ('UPCOMING', 'LIVE')
          AND scheduled_at + (duration_mins * INTERVAL '1 minute') < NOW()
        """, nativeQuery = true)
    int autoEndExpiredMeetings();
}
