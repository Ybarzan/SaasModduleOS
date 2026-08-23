package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customs_invoices", indexes = {
    @Index(name = "idx_customs_invoice_company", columnList = "company_id"),
    @Index(name = "idx_customs_invoice_shipment", columnList = "shipment_id"),
    @Index(name = "idx_customs_invoice_number", columnList = "invoice_number")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomsInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "shipper_name", length = 300)
    private String shipperName;

    @Column(name = "shipper_address", length = 500)
    private String shipperAddress;

    @Column(name = "shipper_city", length = 100)
    private String shipperCity;

    @Column(name = "shipper_country", length = 3)
    private String shipperCountry;

    @Column(name = "shipper_postal_code", length = 20)
    private String shipperPostalCode;

    @Column(name = "consignee_name", length = 300)
    private String consigneeName;

    @Column(name = "consignee_address", length = 500)
    private String consigneeAddress;

    @Column(name = "consignee_city", length = 100)
    private String consigneeCity;

    @Column(name = "consignee_country", length = 3)
    private String consigneeCountry;

    @Column(name = "consignee_postal_code", length = 20)
    private String consigneePostalCode;

    @Column(name = "eori_number", length = 20)
    private String eoriNumber;

    @Column(name = "goods_description", length = 1000)
    private String goodsDescription;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "total_goods_value", precision = 15, scale = 2)
    private BigDecimal totalGoodsValue;

    @Column(name = "total_weight_kg", precision = 15, scale = 3)
    private BigDecimal totalWeightKg;

    @Column(name = "total_packages")
    private Integer totalPackages;

    @Column(name = "incoterm_code", length = 3)
    private String incotermCode;

    @Column(name = "total_duty", precision = 15, scale = 2)
    private BigDecimal totalDuty;

    @Column(name = "total_vat", precision = 15, scale = 2)
    private BigDecimal totalVat;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "notes", length = 2000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    @Entity
    @Table(name = "customs_invoice_items")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class InvoiceItem {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "invoice_id")
        private CustomsInvoice invoice;

        @Column(name = "line_number")
        private Integer lineNumber;

        @Column(name = "sku", length = 100)
        private String sku;

        @Column(name = "name", length = 300)
        private String name;

        @Column(name = "description", length = 1000)
        private String description;

        @Column(name = "hs_code", length = 12)
        private String hsCode;

        @Column(name = "quantity", precision = 15, scale = 2)
        private BigDecimal quantity;

        @Column(name = "unit", length = 10)
        private String unit;

        @Column(name = "unit_price", precision = 15, scale = 2)
        private BigDecimal unitPrice;

        @Column(name = "total_value", precision = 15, scale = 2)
        private BigDecimal totalValue;

        @Column(name = "country_of_origin", length = 3)
        private String countryOfOrigin;

        @Column(name = "duty_rate", precision = 5, scale = 2)
        private BigDecimal dutyRate;

        @Column(name = "duty_type", length = 3)
        private String dutyType;

        @Column(name = "is_preferential")
        private boolean isPreferential;

        @Column(name = "duty_amount", precision = 15, scale = 2)
        private BigDecimal dutyAmount;

        @Column(name = "vat_rate", precision = 5, scale = 2)
        private BigDecimal vatRate;

        @Column(name = "vat_amount", precision = 15, scale = 2)
        private BigDecimal vatAmount;
    }
}
