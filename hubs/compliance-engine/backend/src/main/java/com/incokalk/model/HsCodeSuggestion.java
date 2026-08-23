package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "hs_code_suggestions", indexes = {
    @Index(name = "idx_hs_suggestion_company", columnList = "company_id"),
    @Index(name = "idx_hs_suggestion_created_at", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HsCodeSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(name = "product_description", nullable = false, columnDefinition = "TEXT")
    private String productDescription;

    @Column(name = "suggested_code_1", length = 10)
    private String suggestedCode1;

    @Column(name = "suggested_description_1", columnDefinition = "TEXT")
    private String suggestedDescription1;

    @Column(name = "confidence_1", precision = 3, scale = 2)
    private BigDecimal confidence1;

    @Column(name = "suggested_code_2", length = 10)
    private String suggestedCode2;

    @Column(name = "suggested_description_2", columnDefinition = "TEXT")
    private String suggestedDescription2;

    @Column(name = "confidence_2", precision = 3, scale = 2)
    private BigDecimal confidence2;

    @Column(name = "suggested_code_3", length = 10)
    private String suggestedCode3;

    @Column(name = "suggested_description_3", columnDefinition = "TEXT")
    private String suggestedDescription3;

    @Column(name = "confidence_3", precision = 3, scale = 2)
    private BigDecimal confidence3;

    @Column(name = "user_selection", length = 10)
    private String userSelection;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
