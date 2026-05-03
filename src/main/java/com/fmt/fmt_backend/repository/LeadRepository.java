package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Lead;
import com.fmt.fmt_backend.enums.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    boolean existsByPhone(String phone);

    Optional<Lead> findByPhone(String phone);

    boolean existsByAlternatePhone(String alternatePhone);

    long countByStatus(LeadStatus status);

    long countByStatusIn(List<LeadStatus> statuses);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.status = :status AND l.followupDatetime < :now")
    long countOverdueFollowups(@Param("status") LeadStatus status, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.status = :status AND l.followupDatetime BETWEEN :now AND :soon")
    long countDueSoonFollowups(@Param("status") LeadStatus status,
                               @Param("now") LocalDateTime now,
                               @Param("soon") LocalDateTime soon);

    @Query("SELECT COUNT(l) FROM Lead l " +
           "WHERE l.status NOT IN :terminalStatuses " +
           "AND COALESCE(l.sheetCreatedAt, l.createdAt) BETWEEN :from AND :to")
    long countAgingLeads(
            @Param("terminalStatuses") List<LeadStatus> terminalStatuses,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(l) FROM Lead l " +
           "WHERE l.status NOT IN :terminalStatuses " +
           "AND COALESCE(l.sheetCreatedAt, l.createdAt) < :before")
    long countStaleLeads(
            @Param("terminalStatuses") List<LeadStatus> terminalStatuses,
            @Param("before") LocalDateTime before);

    // Per-rep: active leads assigned to a user
    long countByAssignedToIdAndStatusNotIn(UUID assignedToId, List<LeadStatus> statuses);

    // Per-rep: sales (PAYMENT_DONE) closed by a specific user after a given time
    long countByClosedByIdAndStatusAndUpdatedAtAfter(UUID closedById, LeadStatus status, LocalDateTime after);
}
