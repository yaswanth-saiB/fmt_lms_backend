package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.WebinarRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebinarRegistrationRepository extends JpaRepository<WebinarRegistration, UUID> {

    List<WebinarRegistration> findByWebinarIdOrderByRegisteredAtDesc(UUID webinarId);

    boolean existsByWebinarIdAndPhone(UUID webinarId, String phone);

    long countByWebinarId(UUID webinarId);
}
