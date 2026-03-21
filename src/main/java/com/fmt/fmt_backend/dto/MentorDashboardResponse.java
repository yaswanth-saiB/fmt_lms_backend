package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MentorDashboardResponse {
    private long totalCourses;
    private long totalBatches;
    private long totalClasses;
    private long totalStudents;
    private List<BatchResponse> recentBatches;
    private List<MeetingResponse> upcomingClasses;
}
