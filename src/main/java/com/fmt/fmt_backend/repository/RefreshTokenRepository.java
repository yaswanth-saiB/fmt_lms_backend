package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.RefreshTokenEntity;
import com.fmt.fmt_backend.entity.User;
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
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByToken(String token);

    List<RefreshTokenEntity> findByUserAndRevokedFalse(User user);

    List<RefreshTokenEntity> findByDeviceIdAndRevokedFalse(UUID deviceId);

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true, r.revokedAt = :now WHERE r.user = :user")
    void revokeAllUserTokens(@Param("user") User user, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true, r.revokedAt = :now WHERE r.device.id = :deviceId")
    void revokeAllDeviceTokens(@Param("deviceId") UUID deviceId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :now OR r.revoked = true")
    void deleteExpiredAndRevoked(@Param("now") LocalDateTime now);

    long countByUserAndRevokedFalse(User user);
}