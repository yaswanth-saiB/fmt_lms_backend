package com.fmt.fmt_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fmt.fmt_backend.dto.CreateMeetingRequest;
import com.fmt.fmt_backend.dto.MeetingDTO;
import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Meeting;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.repository.BatchRepository;
import com.fmt.fmt_backend.repository.EnrollmentRepository;
import com.fmt.fmt_backend.repository.MeetingRepository;
import com.fmt.fmt_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final BatchRepository batchRepository;
    private final UserRepository userRepository;
    private final ZoomService zoomService;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional
    public MeetingDTO createMeeting(UUID mentorId, CreateMeetingRequest request) {
        log.info("Creating meeting for batch: {} by mentor: {}", request.getBatchId(), mentorId);

        User mentor = userRepository.findById(mentorId)
                .orElseThrow(() -> new RuntimeException("Mentor not found"));

        Batch batch = batchRepository.findById(request.getBatchId())
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        // Verify mentor owns this batch
        if (!batch.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to create meetings for this batch");
        }

        // Create meeting in Zoom
        JsonNode zoomMeeting = zoomService.createMeetingSync(
                request.getTopic(),
                request.getStartTime(),
                request.getDurationMinutes(),
                mentor.getEmail()
        );

        if (zoomMeeting == null) {
            throw new RuntimeException("Failed to create Zoom meeting");
        }

        // Save meeting in database
        Meeting meeting = Meeting.builder()
                .zoomMeetingId(zoomMeeting.get("id").asText())
                .topic(request.getTopic())
                .description(request.getDescription())
                .batch(batch)
                .mentor(mentor)
                .startTime(request.getStartTime())
                .durationMinutes(request.getDurationMinutes())
                .joinUrl(zoomMeeting.get("join_url").asText())
                .startUrl(zoomMeeting.get("start_url").asText())
                .password(zoomMeeting.has("password") ? zoomMeeting.get("password").asText() : null)
                .status(Meeting.MeetingStatus.SCHEDULED)
                .build();

        Meeting savedMeeting = meetingRepository.save(meeting);
        log.info("✅ Meeting created with ID: {}", savedMeeting.getId());

        return MeetingDTO.fromEntity(savedMeeting);
    }

    @Transactional(readOnly = true)
    public List<MeetingDTO> getStudentUpcomingMeetings(UUID studentId) {
        log.info("Fetching upcoming meetings for student: {}", studentId);

        List<UUID> batchIds = batchRepository.findBatchIdsByStudentId(studentId);

        List<Meeting> meetings = meetingRepository.findUpcomingMeetingsForBatches(
                batchIds, LocalDateTime.now());

        return meetings.stream()
                .map(MeetingDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeetingDTO> getMentorMeetings(UUID mentorId, LocalDateTime from, LocalDateTime to) {
        log.info("Fetching meetings for mentor: {} from {} to {}", mentorId, from, to);

        List<Meeting> meetings = meetingRepository.findMentorMeetingsInRange(mentorId, from, to);

        return meetings.stream()
                .map(MeetingDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MeetingDTO> getBatchMeetings(UUID batchId, UUID userId) {
        log.info("Fetching meetings for batch: {}", batchId);

        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        // Check if user has access (mentor owns batch OR student enrolled)
        boolean hasAccess = batch.getMentor().getId().equals(userId) ||
                enrollmentRepository.existsByStudentIdAndBatchId(userId, batchId);

        if (!hasAccess) {
            throw new RuntimeException("You don't have access to this batch");
        }

        return meetingRepository.findByBatch(batch).stream()
                .map(MeetingDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelMeeting(UUID meetingId, UUID mentorId) {
        log.info("Cancelling meeting: {} by mentor: {}", meetingId, mentorId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getMentor().getId().equals(mentorId)) {
            throw new RuntimeException("You don't have permission to cancel this meeting");
        }

        meeting.setStatus(Meeting.MeetingStatus.CANCELLED);
        meetingRepository.save(meeting);

        // Optionally delete from Zoom
        // zoomService.deleteMeeting(meeting.getZoomMeetingId());
    }

    @Transactional
    public void handleZoomWebhook(String payload) {
        log.info("Processing Zoom webhook: {}", payload);
        // Parse webhook and update meeting status or add recording URL
        // This will be implemented when we add webhook handling
    }
}