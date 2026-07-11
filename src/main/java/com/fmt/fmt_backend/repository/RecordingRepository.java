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
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecordingRepository extends JpaRepository<Recording, UUID> {

    List<Recording> findAllByOrderByCreatedAtDesc();

    List<Recording> findByBatchAndStatusOrderByCreatedAtDesc(Batch batch, RecordingStatus status);

    // Used by mentor/admin to list all recordings for a batch (all statuses)
    List<Recording> findByBatchOrderByCreatedAtDesc(Batch batch);

    // Eagerly fetches meeting to avoid LazyInitializationException in async processing
    // LEFT JOIN so recordings with no meeting (external uploads) are also returned
    @Query("SELECT r FROM Recording r LEFT JOIN FETCH r.meeting WHERE r.id = :id")
    Optional<Recording> findByIdWithMeeting(@Param("id") UUID id);

    // Idempotency check — one meeting now produces N recordings (one per batch);
    // use exists to avoid NonUniqueResultException
    boolean existsByMeeting_ZoomMeetingId(String zoomMeetingId);

    // Used by Bunny webhook — a video ID is shared across all batch recordings for the same meeting
    List<Recording> findAllByBunnyVideoId(String bunnyVideoId);

    // Used in processRecording to copy bunnyVideoId to sibling recordings (same meeting, other batches)
    List<Recording> findByMeeting_IdAndBunnyVideoIdIsNull(UUID meetingId);

    @Query("SELECT r FROM Recording r WHERE r.batch.id IN " +
           "(SELECT e.batch.id FROM BatchEnrollment e WHERE e.student.id = :studentId AND e.isActive = true) " +
           "AND r.status = 'AVAILABLE' ORDER BY r.createdAt DESC")
    List<Recording> findAvailableRecordingsForStudent(@Param("studentId") UUID studentId);

    // Mentor sees their own classes' recordings (via meeting → mentor)
    @Query("SELECT r FROM Recording r WHERE r.meeting.mentor.id = :mentorId ORDER BY r.createdAt DESC")
    List<Recording> findByMentorId(@Param("mentorId") UUID mentorId);

    // Nightly expiry job — find all AVAILABLE recordings past their expiry date
    List<Recording> findByStatusAndExpiresAtBefore(RecordingStatus status, LocalDateTime now);

    // Safety check before Bunny delete — multiple batch rows can share the same bunnyVideoId
    long countByBunnyVideoIdAndIdNot(String bunnyVideoId, UUID excludeId);

    long countByBatch(Batch batch);
}
