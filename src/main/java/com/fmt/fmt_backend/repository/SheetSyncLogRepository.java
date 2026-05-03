package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.SheetSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SheetSyncLogRepository extends JpaRepository<SheetSyncLog, UUID> {

    List<SheetSyncLog> findTop20ByOrderBySyncedAtDesc();
}
