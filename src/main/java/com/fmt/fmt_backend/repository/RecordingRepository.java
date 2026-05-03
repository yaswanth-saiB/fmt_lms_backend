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

    // Used by Zoom webhook — find existing recording for a meeting to avoid duplicates
    Optional<Recording> findByMeeting_ZoomMeetingId(String zoomMeetingId);

    // Used by Bunny webhook — mark recording AVAILABLE once Bunny finishes processing
    Optional<Recording> findByBunnyVideoId(String bunnyVideoId);

    @Query("SELECT r FROM Recording r WHERE r.batch.id IN " +
           "(SELECT e.batch.id FROM BatchEnrollment e WHERE e.student.id = :studentId AND e.isActive = true) " +
           "AND r.status = 'AVAILABLE' AND r.expiresAt > :now ORDER BY r.createdAt DESC")
    List<Recording> findAvailableRecordingsForStudent(@Param("studentId") UUID studentId,
                                                      @Param("now") LocalDateTime now);

    // Mentor sees their own classes' recordings (via meeting → mentor)
    @Query("SELECT r FROM Recording r WHERE r.meeting.mentor.id = :mentorId ORDER BY r.createdAt DESC")
    List<Recording> findByMentorId(@Param("mentorId") UUID mentorId);

    // Nightly expiry job — find all AVAILABLE recordings past their expiry date
    List<Recording> findByStatusAndExpiresAtBefore(RecordingStatus status, LocalDateTime now);

    long countByBatch(Batch batch);
}
