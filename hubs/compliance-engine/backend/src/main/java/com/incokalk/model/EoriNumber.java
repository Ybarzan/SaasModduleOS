package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "eori_numbers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EoriNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(name = "eori", nullable = false, unique = true, length = 20)
    private String eori;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(name = "holder_address")
    private String holderAddress;

    @Column(name = "holder_country", length = 2)
    private String holderCountry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EoriType type = EoriType.EU;

    @Column(name = "is_default")
    @Builder.Default
    private boolean isDefault = true;

    @Column(name = "is_valid")
    @Builder.Default
    private boolean isValid = true;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum EoriType {
        EU, GB, CH
    }
}
