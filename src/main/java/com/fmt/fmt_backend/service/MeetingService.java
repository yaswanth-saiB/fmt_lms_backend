package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.MeetingRequest;
import com.fmt.fmt_backend.dto.MeetingResponse;
import com.fmt.fmt_backend.dto.RescheduleMeetingRequest;
import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Meeting;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.MeetingStatus;
import com.fmt.fmt_backend.repository.BatchRepository;
import com.fmt.fmt_backend.repository.MeetingRepository;
import com.fmt.fmt_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final BatchRepository batchRepository;
    private final UserRepository userRepository;
    private final BatchService batchService;
    private final ZoomService zoomService;

    /**
     * Create a Zoom meeting for a batch.
     * Any mentor can create a class for any batch.
     * Validates that the time slot doesn't conflict with an existing class for the same batch.
     */
    public MeetingResponse createMeeting(MeetingRequest request, UUID mentorId) {
        Batch batch = batchRepository.findById(request.getBatchId())
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        User mentor = userRepository.findById(mentorId)
                .orElseThrow(() -> new RuntimeException("Mentor not found"));

        int duration = request.getDurationMins() != null ? request.getDurationMins() : 120;
        LocalDateTime newStart = request.getScheduledAt();
        LocalDateTime newEnd = newStart != null ? newStart.plusMinutes(duration) : null;

        // Time conflict check — same batch cannot have overlapping classes
        if (newStart != null) {
            List<Meeting> existing = meetingRepository.findActiveOrUpcomingByBatch(batch);
            for (Meeting m : existing) {
                if (m.getScheduledAt() == null) continue;
                LocalDateTime existEnd = m.getScheduledAt().plusMinutes(m.getDurationMins());
                boolean overlaps = m.getScheduledAt().isBefore(newEnd) && existEnd.isAfter(newStart);
                if (overlaps) {
                    throw new RuntimeException(
                        "Time conflict: this batch already has a class scheduled from " +
                        m.getScheduledAt() + " to " + existEnd + " (\"" + m.getTopic() + "\")");
                }
            }
        }

        Map<String, String> zoomMeeting = zoomService.createMeeting(request.getTopic(), duration);

        Meeting meeting = Meeting.builder()
                .batch(batch)
                .mentor(mentor)
                .zoomMeetingId(zoomMeeting.get("id"))
                .topic(request.getTopic())
                .startUrl(zoomMeeting.get("start_url"))
                .joinUrl(zoomMeeting.get("join_url"))
                .durationMins(duration)
                .scheduledAt(newStart)
                .status(MeetingStatus.UPCOMING)
                .build();

        Meeting saved = meetingRepository.save(meeting);
        log.info("Meeting created: id={}, batch={}, mentor={}, at={}", saved.getId(), batch.getId(), mentorId, newStart);
        return toMentorResponse(saved);
    }

    /**
     * Mark a meeting as LIVE (mentor clicked Start Meeting).
     * Only the conducting mentor (or admin via null check) can start their own meeting.
     */
    @Transactional
    public MeetingResponse startMeeting(UUID meetingId, UUID mentorId) {
        Meeting meeting = getMeetingForMentor(meetingId, mentorId);

        if (meeting.getStatus() != MeetingStatus.UPCOMING) {
            throw new RuntimeException("Only UPCOMING meetings can be started. Current status: " + meeting.getStatus());
        }

        meeting.setStatus(MeetingStatus.LIVE);
        meeting.setStartedAt(LocalDateTime.now());
        Meeting saved = meetingRepository.save(meeting);
        log.info("Meeting {} marked LIVE by mentor {}", meetingId, mentorId);
        return toMentorResponse(saved);
    }

    /**
     * Mark a meeting as ENDED (mentor finished the class).
     */
    @Transactional
    public MeetingResponse endMeeting(UUID meetingId, UUID mentorId) {
        Meeting meeting = getMeetingForMentor(meetingId, mentorId);

        if (meeting.getStatus() != MeetingStatus.LIVE) {
            throw new RuntimeException("Only LIVE meetings can be ended. Current status: " + meeting.getStatus());
        }

        meeting.setStatus(MeetingStatus.ENDED);
        meeting.setEndedAt(LocalDateTime.now());
        Meeting saved = meetingRepository.save(meeting);
        log.info("Meeting {} marked ENDED by mentor {}", meetingId, mentorId);
        return toMentorResponse(saved);
    }

    /**
     * Cancel an UPCOMING meeting (soft delete — sets CANCELLED).
     * Cannot cancel a LIVE or already ENDED meeting.
     */
    @Transactional
    public void cancelMeeting(UUID meetingId, UUID mentorId) {
        Meeting meeting = getMeetingForMentor(meetingId, mentorId);

        if (meeting.getStatus() != MeetingStatus.UPCOMING) {
            throw new RuntimeException("Only UPCOMING meetings can be cancelled. Current status: " + meeting.getStatus());
        }

        meeting.setStatus(MeetingStatus.CANCELLED);
        meetingRepository.save(meeting);
        log.info("Meeting {} cancelled by mentor {}", meetingId, mentorId);
    }

    /**
     * Reschedule an UPCOMING meeting — update time, duration, and/or topic.
     * Re-validates for time conflicts after rescheduling.
     */
    @Transactional
    public MeetingResponse rescheduleMeeting(UUID meetingId, RescheduleMeetingRequest request, UUID mentorId) {
        Meeting meeting = getMeetingForMentor(meetingId, mentorId);

        if (meeting.getStatus() != MeetingStatus.UPCOMING) {
            throw new RuntimeException("Only UPCOMING meetings can be rescheduled. Current status: " + meeting.getStatus());
        }

        int duration = request.getDurationMins() != null ? request.getDurationMins() : meeting.getDurationMins();
        LocalDateTime newStart = request.getScheduledAt();
        LocalDateTime newEnd = newStart.plusMinutes(duration);

        // Conflict check — exclude this meeting itself
        List<Meeting> existing = meetingRepository.findActiveOrUpcomingByBatch(meeting.getBatch());
        for (Meeting m : existing) {
            if (m.getId().equals(meetingId)) continue; // skip self
            if (m.getScheduledAt() == null) continue;
            LocalDateTime existEnd = m.getScheduledAt().plusMinutes(m.getDurationMins());
            boolean overlaps = m.getScheduledAt().isBefore(newEnd) && existEnd.isAfter(newStart);
            if (overlaps) {
                throw new RuntimeException(
                    "Time conflict: this batch already has a class from " +
                    m.getScheduledAt() + " to " + existEnd + " (\"" + m.getTopic() + "\")");
            }
        }

        meeting.setScheduledAt(newStart);
        meeting.setDurationMins(duration);
        if (request.getTopic() != null && !request.getTopic().isBlank()) {
            meeting.setTopic(request.getTopic());
        }

        Meeting saved = meetingRepository.save(meeting);
        log.info("Meeting {} rescheduled to {} by mentor {}", meetingId, newStart, mentorId);
        return toMentorResponse(saved);
    }

    // ---------------------------------------------------------------
    // Admin variants — same logic, no ownership check
    // ---------------------------------------------------------------

    @Transactional
    public MeetingResponse adminStartMeeting(UUID meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));
        if (meeting.getStatus() != MeetingStatus.UPCOMING) {
            throw new RuntimeException("Only UPCOMING meetings can be started. Current status: " + meeting.getStatus());
        }
        meeting.setStatus(MeetingStatus.LIVE);
        meeting.setStartedAt(LocalDateTime.now());
        return toMentorResponse(meetingRepository.save(meeting));
    }

    @Transactional
    public MeetingResponse adminEndMeeting(UUID meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));
        // Admin can force-end LIVE or UPCOMING (e.g. meeting stuck because webhook never fired)
        if (meeting.getStatus() != MeetingStatus.LIVE && meeting.getStatus() != MeetingStatus.UPCOMING) {
            throw new RuntimeException("Only LIVE or UPCOMING meetings can be ended. Current status: " + meeting.getStatus());
        }
        meeting.setStatus(MeetingStatus.ENDED);
        meeting.setEndedAt(LocalDateTime.now());
        log.info("Meeting {} force-ended by admin (was {})", meetingId, meeting.getStatus());
        return toMentorResponse(meetingRepository.save(meeting));
    }

    @Transactional
    public void adminCancelMeeting(UUID meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));
        if (meeting.getStatus() != MeetingStatus.UPCOMING) {
            throw new RuntimeException("Only UPCOMING meetings can be cancelled. Current status: " + meeting.getStatus());
        }
        meeting.setStatus(MeetingStatus.CANCELLED);
        meetingRepository.save(meeting);
    }

    @Transactional
    public MeetingResponse adminRescheduleMeeting(UUID meetingId, RescheduleMeetingRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));
        if (meeting.getStatus() != MeetingStatus.UPCOMING) {
            throw new RuntimeException("Only UPCOMING meetings can be rescheduled. Current status: " + meeting.getStatus());
        }

        int duration = request.getDurationMins() != null ? request.getDurationMins() : meeting.getDurationMins();
        LocalDateTime newStart = request.getScheduledAt();
        LocalDateTime newEnd = newStart.plusMinutes(duration);

        List<Meeting> existing = meetingRepository.findActiveOrUpcomingByBatch(meeting.getBatch());
        for (Meeting m : existing) {
            if (m.getId().equals(meetingId)) continue;
            if (m.getScheduledAt() == null) continue;
            LocalDateTime existEnd = m.getScheduledAt().plusMinutes(m.getDurationMins());
            if (m.getScheduledAt().isBefore(newEnd) && existEnd.isAfter(newStart)) {
                throw new RuntimeException(
                    "Time conflict: this batch already has a class from " +
                    m.getScheduledAt() + " to " + existEnd + " (\"" + m.getTopic() + "\")");
            }
        }

        meeting.setScheduledAt(newStart);
        meeting.setDurationMins(duration);
        if (request.getTopic() != null && !request.getTopic().isBlank()) {
            meeting.setTopic(request.getTopic());
        }
        return toMentorResponse(meetingRepository.save(meeting));
    }

    // ---------------------------------------------------------------
    // Read methods
    // ---------------------------------------------------------------

    public MeetingResponse getJoinUrlForStudent(UUID meetingId, UUID studentId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));
        if (!batchService.isStudentEnrolled(meeting.getBatch().getId(), studentId)) {
            throw new RuntimeException("You are not enrolled in this batch");
        }
        return toStudentResponse(meeting);
    }

    public List<MeetingResponse> getUpcomingMeetingsForStudent(UUID studentId) {
        return meetingRepository.findUpcomingMeetingsForStudent(studentId)
                .stream().map(this::toStudentResponse).collect(Collectors.toList());
    }

    public List<MeetingResponse> getMentorMeetings(UUID mentorId) {
        return getMentorMeetings(mentorId, null);
    }

    public List<MeetingResponse> getMentorMeetings(UUID mentorId, List<MeetingStatus> statusFilter) {
        return meetingRepository.findByMentorId(mentorId)
                .stream()
                .filter(m -> statusFilter == null || statusFilter.isEmpty() || statusFilter.contains(m.getStatus()))
                .map(this::toMentorResponse)
                .collect(Collectors.toList());
    }

    public List<MeetingResponse> getBatchMeetingsForMentor(UUID batchId, UUID mentorId) {
        return getBatchMeetingsForMentor(batchId, mentorId, null);
    }

    public List<MeetingResponse> getBatchMeetingsForMentor(UUID batchId, UUID mentorId, List<MeetingStatus> statusFilter) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));
        return meetingRepository.findByBatchOrderByCreatedAtDesc(batch)
                .stream()
                .filter(m -> statusFilter == null || statusFilter.isEmpty() || statusFilter.contains(m.getStatus()))
                .map(this::toMentorResponse)
                .collect(Collectors.toList());
    }

    public List<MeetingResponse> getBatchMeetingsForStudent(UUID batchId, UUID studentId) {
        return getBatchMeetingsForStudent(batchId, studentId, null);
    }

    public List<MeetingResponse> getBatchMeetingsForStudent(UUID batchId, UUID studentId, List<MeetingStatus> statusFilter) {
        if (!batchService.isStudentEnrolled(batchId, studentId)) {
            throw new RuntimeException("You are not enrolled in this batch");
        }
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));
        return meetingRepository.findByBatchOrderByCreatedAtDesc(batch)
                .stream()
                .filter(m -> statusFilter == null || statusFilter.isEmpty() || statusFilter.contains(m.getStatus()))
                .map(this::toStudentResponse)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /** Load meeting and verify the requesting mentor is the one conducting it. */
    private Meeting getMeetingForMentor(UUID meetingId, UUID mentorId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));
        if (!meeting.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You can only manage classes you are conducting");
        }
        return meeting;
    }

    /** Admin response — same as mentor (includes start_url + zoomMeetingId) */
    public MeetingResponse toAdminResponse(Meeting m) {
        return toMentorResponse(m);
    }

    /** Mentor/admin response — includes start_url and mentor info */
    private MeetingResponse toMentorResponse(Meeting m) {
        return MeetingResponse.builder()
                .id(m.getId())
                .zoomMeetingId(m.getZoomMeetingId())
                .topic(m.getTopic())
                .batchId(m.getBatch().getId())
                .batchName(m.getBatch().getName())
                .mentorId(m.getMentor().getId())
                .mentorName(m.getMentor().getFirstName() + " " + m.getMentor().getLastName())
                .status(m.getStatus())
                .scheduledAt(m.getScheduledAt())
                .durationMins(m.getDurationMins())
                .createdAt(m.getCreatedAt())
                .startUrl(m.getStartUrl())
                .joinUrl(m.getJoinUrl())
                .build();
    }

    /** Student response — NEVER includes start_url, includes mentorName for display */
    private MeetingResponse toStudentResponse(Meeting m) {
        return MeetingResponse.builder()
                .id(m.getId())
                .topic(m.getTopic())
                .batchId(m.getBatch().getId())
                .batchName(m.getBatch().getName())
                .mentorName(m.getMentor().getFirstName() + " " + m.getMentor().getLastName())
                .status(m.getStatus())
                .scheduledAt(m.getScheduledAt())
                .durationMins(m.getDurationMins())
                .createdAt(m.getCreatedAt())
                .joinUrl(m.getJoinUrl())
                // startUrl and mentorId intentionally omitted for students
                .build();
    }
}
