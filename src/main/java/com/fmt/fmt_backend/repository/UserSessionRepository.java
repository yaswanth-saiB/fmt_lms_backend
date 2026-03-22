package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findBySessionIdAndActiveTrue(UUID sessionId);

    List<UserSession> findByUserAndActiveTrueOrderByCreatedAtDesc(User user);

    long countByUserAndActiveTrue(User user);

    @Modifying
    @Query("UPDATE UserSession s SET s.active = false, s.revokedAt = :now, s.revokedReason = 'logout-all' " +
           "WHERE s.user = :user AND s.active = true")
    void revokeAllUserSessions(@Param("user") User user, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserSession s SET s.active = false, s.revokedAt = :now, s.revokedReason = 'device-revoked' " +
           "WHERE s.device.id = :deviceId AND s.active = true")
    void revokeDeviceSessions(@Param("deviceId") UUID deviceId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE UserSession s SET s.active = false, s.revokedAt = :now, s.revokedReason = 'logout' " +
           "WHERE s.sessionId = :sessionId")
    void revokeSession(@Param("sessionId") UUID sessionId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now AND s.active = false")
    void deleteExpiredInactive(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.user = :user")
    void deleteByUser(@Param("user") User user);
}
