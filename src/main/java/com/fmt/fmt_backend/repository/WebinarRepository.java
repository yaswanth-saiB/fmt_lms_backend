package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Webinar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebinarRepository extends JpaRepository<Webinar, UUID> {

    List<Webinar> findAllByOrderByScheduledAtDesc();

    List<Webinar> findAllByIsActiveTrueOrderByScheduledAtDesc();
}
