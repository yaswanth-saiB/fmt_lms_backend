package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Meeting;
import com.fmt.fmt_backend.enums.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    List<Meeting> findByBatchOrderByCreatedAtDesc(Batch batch);

    List<Meeting> findByBatchAndStatusOrderByScheduledAtAsc(Batch batch, MeetingStatus status);

    Optional<Meeting> findByZoomMeetingId(String zoomMeetingId);

    @Query("SELECT m FROM Meeting m WHERE m.batch.id IN " +
           "(SELECT e.batch.id FROM BatchEnrollment e WHERE e.student.id = :studentId AND e.isActive = true) " +
           "AND m.status = 'UPCOMING' ORDER BY m.scheduledAt ASC")
    List<Meeting> findUpcomingMeetingsForStudent(@Param("studentId") UUID studentId);

    @Query("SELECT m FROM Meeting m WHERE m.batch.course.mentor.id = :mentorId ORDER BY m.createdAt DESC")
    List<Meeting> findByMentorId(@Param("mentorId") UUID mentorId);

    @Query("SELECT COUNT(m) FROM Meeting m WHERE m.batch.course.mentor.id = :mentorId")
    long countByMentorId(@Param("mentorId") UUID mentorId);

    // Admin — all upcoming classes across all batches
    @Query("SELECT m FROM Meeting m WHERE m.status = 'UPCOMING' ORDER BY m.scheduledAt ASC")
    List<Meeting> findAllUpcoming(org.springframework.data.domain.Pageable pageable);
}
