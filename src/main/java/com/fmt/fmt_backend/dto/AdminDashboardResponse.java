package com.fmt.fmt_backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminDashboardResponse {
    // Platform-wide counts
    private long totalUsers;
    private long totalStudents;
    private long totalMentors;
    private long totalCourses;
    private long totalBatches;
    private long totalClasses;
    private long totalRecordings;

    // Snapshot lists
    private List<MeetingResponse> upcomingClasses;   // next 5 across all batches
    private List<UserResponse> recentUsers;          // last 5 registered users
}
