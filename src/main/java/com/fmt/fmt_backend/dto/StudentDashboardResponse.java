package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StudentDashboardResponse {
    private long enrolledCoursesCount;
    private List<BatchResponse> enrolledBatches;
    private List<MeetingResponse> upcomingClasses;
}
