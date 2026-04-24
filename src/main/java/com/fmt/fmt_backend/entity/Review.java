package com.fmt.fmt_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reviewer_name", nullable = false, length = 100)
    private String reviewerName;

    @Column(nullable = false)
    private Integer rating;

    @Column(name = "review_text", nullable = false, columnDefinition = "TEXT")
    private String reviewText;

    // Stored as string — Google shows "2 months ago" or "April 2024"
    @Column(name = "review_date", length = 50)
    private String reviewDate;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    // Lower number = shown first on homepage
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @PrePersist
    public void prePersist() {
        if (displayOrder == null) displayOrder = 0;
    }
}
