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

    @Query("SELECT a FROM LeadActivity a WHERE a.lead.id = :leadId AND a.activityType = 'NOTE_ADDED' AND a.description NOT LIKE '[WA Inbox]%' ORDER BY a.createdAt DESC")
    List<LeadActivity> findLeadPageNotesForLead(@Param("leadId") UUID leadId);

    void deleteByLeadId(UUID leadId);

    @Query("SELECT COUNT(la) FROM LeadActivity la " +
           "WHERE la.activityType = :activityType " +
           "AND la.description LIKE :prefix " +
           "AND la.createdAt >= :start")
    long countStatusChangeAfter(
            @Param("activityType") ActivityType activityType,
            @Param("prefix") String prefix,
            @Param("start") LocalDateTime start);

    // Per-rep: demos or other status changes done by a specific user
    @Query("SELECT COUNT(la) FROM LeadActivity la " +
           "WHERE la.createdBy.id = :userId " +
           "AND la.activityType = :activityType " +
           "AND la.description LIKE :prefix " +
           "AND la.createdAt >= :start")
    long countStatusChangeByUserAfter(
            @Param("userId") UUID userId,
            @Param("activityType") ActivityType activityType,
            @Param("prefix") String prefix,
            @Param("start") LocalDateTime start);
}
