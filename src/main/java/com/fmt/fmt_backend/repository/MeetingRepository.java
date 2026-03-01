package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Meeting;
import com.fmt.fmt_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    List<Meeting> findByBatch(Batch batch);

    List<Meeting> findByMentor(User mentor);

    List<Meeting> findByBatchAndStartTimeAfter(Batch batch, LocalDateTime time);

    @Query("SELECT m FROM Meeting m WHERE m.batch.id IN :batchIds AND m.startTime > :now ORDER BY m.startTime ASC")
    List<Meeting> findUpcomingMeetingsForBatches(@Param("batchIds") List<UUID> batchIds, @Param("now") LocalDateTime now);

    @Query("SELECT m FROM Meeting m WHERE m.mentor.id = :mentorId AND m.startTime BETWEEN :start AND :end")
    List<Meeting> findMentorMeetingsInRange(@Param("mentorId") UUID mentorId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    Optional<Meeting> findByZoomMeetingId(String zoomMeetingId);
}