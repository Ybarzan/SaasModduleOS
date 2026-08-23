package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_balances", indexes = {
    @Index(name = "uq_stock_balances_warehouse_item", columnList = "warehouse_id, item_id", unique = true),
    @Index(name = "idx_stock_balances_company", columnList = "company_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "quantity_on_hand", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Column(name = "quantity_reserved", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal quantityReserved = BigDecimal.ZERO;

    @Column(name = "quantity_in_transit", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal quantityInTransit = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}
