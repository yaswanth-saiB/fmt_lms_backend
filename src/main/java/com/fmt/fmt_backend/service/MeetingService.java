package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.MeetingRequest;
import com.fmt.fmt_backend.dto.MeetingResponse;
import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Meeting;
import com.fmt.fmt_backend.enums.MeetingStatus;
import com.fmt.fmt_backend.repository.BatchRepository;
import com.fmt.fmt_backend.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final BatchService batchService;
    private final ZoomService zoomService;

    /**
     * MENTOR: Create a Zoom meeting for a batch.
     * Returns start_url (mentor) and join_url (students).
     */
    public MeetingResponse createMeeting(MeetingRequest request, UUID mentorId) {
        Batch batch = batchRepository.findById(request.getBatchId())
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        if (!batch.getCourse().getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You can only create meetings for your own batches");
        }

        int duration = request.getDurationMins() != null ? request.getDurationMins() : 120;

        // Call Zoom API
        Map<String, String> zoomMeeting = zoomService.createMeeting(request.getTopic(), duration);

        Meeting meeting = Meeting.builder()
                .batch(batch)
                .zoomMeetingId(zoomMeeting.get("id"))
                .topic(request.getTopic())
                .startUrl(zoomMeeting.get("start_url"))
                .joinUrl(zoomMeeting.get("join_url"))
                .durationMins(duration)
                .scheduledAt(request.getScheduledAt())
                .status(MeetingStatus.UPCOMING)
                .build();

        Meeting saved = meetingRepository.save(meeting);
        log.info("Meeting created: id={}, zoomId={}, batch={}", saved.getId(), saved.getZoomMeetingId(), batch.getId());

        // Return response WITH start_url for mentor
        return toMentorResponse(saved);
    }

    /**
     * STUDENT: Get join_url for a meeting in a batch they're enrolled in.
     * Never returns start_url.
     */
    public MeetingResponse getJoinUrlForStudent(UUID meetingId, UUID studentId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        boolean enrolled = batchService.isStudentEnrolled(meeting.getBatch().getId(), studentId);
        if (!enrolled) {
            throw new RuntimeException("You are not enrolled in this batch");
        }

        return toStudentResponse(meeting);
    }

    /**
     * STUDENT: Get all upcoming meetings for batches the student is enrolled in.
     */
    public List<MeetingResponse> getUpcomingMeetingsForStudent(UUID studentId) {
        return meetingRepository.findUpcomingMeetingsForStudent(studentId)
                .stream().map(this::toStudentResponse).collect(Collectors.toList());
    }

    /**
     * MENTOR: Get all meetings they created.
     */
    public List<MeetingResponse> getMentorMeetings(UUID mentorId) {
        return meetingRepository.findByMentorId(mentorId)
                .stream().map(this::toMentorResponse).collect(Collectors.toList());
    }

    /**
     * Get all meetings for a specific batch (mentor view — includes start_url).
     */
    public List<MeetingResponse> getBatchMeetingsForMentor(UUID batchId, UUID mentorId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        if (!batch.getCourse().getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("Access denied");
        }

        return meetingRepository.findByBatchOrderByCreatedAtDesc(batch)
                .stream().map(this::toMentorResponse).collect(Collectors.toList());
    }

    /**
     * Get all meetings for a specific batch (student view — no start_url).
     */
    public List<MeetingResponse> getBatchMeetingsForStudent(UUID batchId, UUID studentId) {
        if (!batchService.isStudentEnrolled(batchId, studentId)) {
            throw new RuntimeException("You are not enrolled in this batch");
        }

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        return meetingRepository.findByBatchOrderByCreatedAtDesc(batch)
                .stream().map(this::toStudentResponse).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /** Admin response — same as mentor (includes start_url + zoomMeetingId) */
    public MeetingResponse toAdminResponse(Meeting m) {
        return toMentorResponse(m);
    }

    /** Mentor response — includes start_url */
    private MeetingResponse toMentorResponse(Meeting m) {
        return MeetingResponse.builder()
                .id(m.getId())
                .zoomMeetingId(m.getZoomMeetingId())
                .topic(m.getTopic())
                .batchId(m.getBatch().getId())
                .batchName(m.getBatch().getName())
                .status(m.getStatus())
                .scheduledAt(m.getScheduledAt())
                .durationMins(m.getDurationMins())
                .createdAt(m.getCreatedAt())
                .startUrl(m.getStartUrl())   // Mentor gets start_url
                .joinUrl(m.getJoinUrl())
                .build();
    }

    /** Student response — NEVER includes start_url */
    private MeetingResponse toStudentResponse(Meeting m) {
        return MeetingResponse.builder()
                .id(m.getId())
                .topic(m.getTopic())
                .batchId(m.getBatch().getId())
                .batchName(m.getBatch().getName())
                .status(m.getStatus())
                .scheduledAt(m.getScheduledAt())
                .durationMins(m.getDurationMins())
                .createdAt(m.getCreatedAt())
                .joinUrl(m.getJoinUrl())
                // startUrl intentionally omitted
                .build();
    }
}
