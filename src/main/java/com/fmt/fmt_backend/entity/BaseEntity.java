package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@MappedSuperclass  // This class won't be a table, but its fields will be in child tables
@Getter
@Setter
public abstract class BaseEntity {

    @CreationTimestamp  // Auto-set when entity is created
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp    // Auto-updates when entity changes
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

// WHY BaseEntity?
// 1. DRY Principle: Don't Repeat Yourself
// 2. All entities need createdAt, updatedAt
// 3. Automatic timestamp management
// 4. Consistent field names across tables
