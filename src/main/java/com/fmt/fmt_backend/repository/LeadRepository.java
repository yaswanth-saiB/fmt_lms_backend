package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Lead;
import com.fmt.fmt_backend.enums.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    boolean existsByPhone(String phone);

    long countByStatus(LeadStatus status);

    long countByStatusIn(List<LeadStatus> statuses);

    @Query("SELECT COUNT(l) FROM Lead l WHERE l.status = :status AND l.followupDatetime < :now")
    long countOverdueFollowups(@Param("status") LeadStatus status, @Param("now") LocalDateTime now);
}
