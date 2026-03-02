package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Enquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EnquiryRepository extends JpaRepository<Enquiry, UUID> {

    List<Enquiry> findByStatus(Enquiry.EnquiryStatus status);

    @Query("SELECT e FROM Enquiry e WHERE e.createdAt BETWEEN :start AND :end")
    List<Enquiry> findEnquiriesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ✅ FIXED: Use BETWEEN with start and end of day
    @Query("SELECT COUNT(e) FROM Enquiry e WHERE e.createdAt BETWEEN :startOfDay AND :endOfDay")
    long countTodayEnquiries(@Param("startOfDay") LocalDateTime startOfDay,
                             @Param("endOfDay") LocalDateTime endOfDay);

    List<Enquiry> findByMobile(String mobile);
}