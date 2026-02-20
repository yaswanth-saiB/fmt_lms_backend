package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.DeviceEntity;
import com.fmt.fmt_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    List<DeviceEntity> findByUserAndIsActiveTrue(User user);

    Optional<DeviceEntity> findByUserAndDeviceFingerprint(User user, String fingerprint);

    long countByUserAndIsActiveTrue(User user);

    long countByUserAndIsStreamingTrue(User user);

    @Modifying
    @Query("UPDATE DeviceEntity d SET d.isActive = false WHERE d.user = :user AND d.id NOT IN :excludeIds")
    void deactivateOtherDevices(@Param("user") User user, @Param("excludeIds") List<UUID> excludeIds);
}
