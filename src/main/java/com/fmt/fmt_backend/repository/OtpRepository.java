package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.OtpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpRepository extends JpaRepository<OtpEntity, UUID> {

    Optional<OtpEntity> findTopByIdentifierAndTypeAndVerifiedFalseOrderByCreatedAtDesc(
            String identifier, OtpEntity.OtpType type);

    @Modifying
    @Query("DELETE FROM OtpEntity o WHERE o.expiresAt < :now")
    void deleteExpiredOtps(@Param("now") LocalDateTime now);

    long countByIdentifierAndCreatedAtAfter(String identifier, LocalDateTime after);
}