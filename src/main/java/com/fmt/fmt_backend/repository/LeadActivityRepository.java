package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.LeadActivity;
import com.fmt.fmt_backend.enums.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

    List<LeadActivity> findByLeadIdOrderByCreatedAtAsc(UUID leadId);

    @Query("SELECT COUNT(la) FROM LeadActivity la " +
           "WHERE la.activityType = :activityType " +
           "AND la.description LIKE :prefix " +
           "AND la.createdAt >= :start")
    long countStatusChangeAfter(
            @Param("activityType") ActivityType activityType,
            @Param("prefix") String prefix,
            @Param("start") LocalDateTime start);
}
