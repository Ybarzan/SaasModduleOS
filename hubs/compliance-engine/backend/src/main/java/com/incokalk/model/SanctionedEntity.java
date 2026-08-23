package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sanctioned_entities", indexes = {
        @Index(name = "idx_sanctioned_name", columnList = "name"),
        @Index(name = "idx_sanctioned_country", columnList = "country_code"),
        @Index(name = "idx_sanctioned_list_source", columnList = "list_source"),
        @Index(name = "idx_sanctioned_active", columnList = "is_active")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SanctionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "list_source", nullable = false)
    private String listSource;

    @Column(name = "entry_id", nullable = false)
    private String entryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type")
    private EntityType entityType;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String aliases;

    @Column(name = "country_code")
    private String countryCode;

    private String reason;

    private String program;

    @Column(name = "list_date")
    private LocalDate listDate;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    public enum EntityType {
        PERSON, ENTITY, VESSEL, ADDRESS
    }
}
