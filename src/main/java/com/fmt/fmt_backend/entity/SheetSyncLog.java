package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sheet_sync_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SheetSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "synced_at", updatable = false)
    private LocalDateTime syncedAt;

    @Column(name = "sheet_rows_read")
    private int sheetRowsRead;

    @Column(name = "imported_count")
    private int importedCount;

    @Column(name = "skipped_count")
    private int skippedCount;

    // SCHEDULED or MANUAL
    @Column(name = "trigger_type", length = 20)
    private String triggerType;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by")
    private User triggeredBy;

    // Newline-separated error messages (null = no errors)
    @Column(name = "errors", columnDefinition = "TEXT")
    private String errors;
}
