package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.LeadPayment;
import com.fmt.fmt_backend.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LeadPaymentRepository extends JpaRepository<LeadPayment, UUID> {

    List<LeadPayment> findByLeadIdOrderByCreatedAtAsc(UUID leadId);

    void deleteByLeadId(UUID leadId);

    @Query("SELECT COALESCE(SUM(lp.amount), 0) FROM LeadPayment lp " +
           "WHERE lp.lead.id = :leadId AND lp.status = :status")
    BigDecimal sumAmountByLeadIdAndStatus(
            @Param("leadId") UUID leadId,
            @Param("status") PaymentStatus status);
}
