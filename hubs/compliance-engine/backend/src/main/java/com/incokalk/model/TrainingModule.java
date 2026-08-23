package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "training_modules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrainingModule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 100)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Difficulty difficulty;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "content_html", columnDefinition = "TEXT")
    private String contentHtml;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quiz_data", columnDefinition = "jsonb")
    private Map<String, Object> quizData;

    @Column(name = "passing_score")
    @Builder.Default
    private Integer passingScore = 80;

    @Column(name = "is_published")
    @Builder.Default
    private Boolean isPublished = false;

    @Version
    private Integer version;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Category {
        INCOTERMS, CUSTOMS, LOGISTICS, COMPLIANCE, CSRD
    }

    public enum Difficulty {
        BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
    }
}
