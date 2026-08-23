package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "simulations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Simulation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    @JsonIgnore
    private Company company;

    @Column(name = "incoterm_code", nullable = false, length = 5)
    private String incotermCode;

    @Column(name = "origin_country", nullable = false, length = 3)
    private String originCountry;

    @Column(name = "destination_country", nullable = false, length = 3)
    private String destinationCountry;

    @Column(name = "goods_value", nullable = false)
    private double goodsValue;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "transport_mode", length = 20)
    private String transportMode;

    @Column(name = "hs_code", length = 10)
    private String hsCode;

    @Column(name = "total_buyer_cost")
    private double totalBuyerCost;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private Map<String, Object> resultJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
 