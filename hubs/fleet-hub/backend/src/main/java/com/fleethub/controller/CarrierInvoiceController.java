package com.fleethub.controller;

import com.fleethub.model.Company;
import com.fleethub.model.CarrierInvoice;
import com.fleethub.model.CarrierInvoice.InvoiceStatus;
import com.fleethub.security.TenantContext;
import com.fleethub.service.CarrierInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Facturation transporteur (AP) — porté depuis compliance-engine. L'original
 * gatait tout le contrôleur derrière {@code @RequiresPlan(ENTERPRISE)} ; FleetHub
 * n'a pas de système de plan-gate générique par endpoint, donc on applique
 * explicitement la même règle ici avec {@code Company.SubscriptionPlan}
 * existant (voir requireEnterprisePlan()), au lieu d'inventer un nouveau mécanisme.
 */
@RestController
@RequestMapping("/api/carrier-invoices")
@RequiredArgsConstructor
@Tag(name = "Carrier Invoices", description = "Facturation transporteur (AP)")
public class CarrierInvoiceController {

    private final CarrierInvoiceService invoiceService;

    @GetMapping
    @Operation(summary = "Lister les factures transporteur")
    public ResponseEntity<?> listAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        requireEnterprisePlan();
        if (page != null && size != null && size > 0) {
            Page<CarrierInvoice> result = invoiceService.getAll(PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(invoiceService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir une facture transporteur par ID")
    public ResponseEntity<CarrierInvoice> getById(@PathVariable UUID id) {
        requireEnterprisePlan();
        return ResponseEntity.ok(invoiceService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Créer une facture transporteur")
    public ResponseEntity<CarrierInvoice> create(@Valid @RequestBody CreateCarrierInvoice body) {
        requireEnterprisePlan();
        CarrierInvoice invoice = CarrierInvoice.builder()
                .invoiceNumber(body.invoiceNumber())
                .invoiceDate(body.invoiceDate())
                .dueDate(body.dueDate())
                .carrierName(body.carrierName())
                .carrierReference(body.carrierReference())
                .totalAmount(body.totalAmount())
                .currency(body.currency())
                .freightAmount(body.freightAmount())
                .fuelSurcharge(body.fuelSurcharge())
                .securityFee(body.securityFee())
                .handlingFee(body.handlingFee())
                .customsFee(body.customsFee())
                .otherCharges(body.otherCharges())
                .otherChargesDescription(body.otherChargesDescription())
                .shipmentReference(body.shipmentReference())
                .negotiatedRate(body.negotiatedRate())
                .reconciliationNotes(body.reconciliationNotes())
                .build();
        return ResponseEntity.ok(invoiceService.create(invoice));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Mettre à jour le statut d'une facture transporteur")
    public ResponseEntity<CarrierInvoice> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdate body) {
        requireEnterprisePlan();
        InvoiceStatus newStatus = InvoiceStatus.valueOf(body.status());
        return ResponseEntity.ok(invoiceService.updateStatus(id, newStatus, body.reason()));
    }

    @PutMapping("/{id}/reconcile")
    @Operation(summary = "Réconcilier une facture transporteur")
    public ResponseEntity<CarrierInvoice> reconcile(
            @PathVariable UUID id,
            @Valid @RequestBody ReconcileRequest body) {
        requireEnterprisePlan();
        return ResponseEntity.ok(invoiceService.reconcile(id, body.negotiatedRate(), body.notes()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une facture transporteur")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        requireEnterprisePlan();
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques des factures transporteur")
    public ResponseEntity<Map<String, Object>> getStats() {
        requireEnterprisePlan();
        return ResponseEntity.ok(invoiceService.getStats());
    }

    private void requireEnterprisePlan() {
        if (TenantContext.require().getPlan() != Company.SubscriptionPlan.ENTERPRISE) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "La facturation transporteur est réservée à la formule ENTERPRISE.");
        }
    }

    public record CreateCarrierInvoice(
            @NotNull String invoiceNumber,
            @NotNull LocalDate invoiceDate,
            LocalDate dueDate,
            String carrierName,
            String carrierReference,
            @NotNull BigDecimal totalAmount,
            String currency,
            BigDecimal freightAmount,
            BigDecimal fuelSurcharge,
            BigDecimal securityFee,
            BigDecimal handlingFee,
            BigDecimal customsFee,
            BigDecimal otherCharges,
            String otherChargesDescription,
            String shipmentReference,
            BigDecimal negotiatedRate,
            String reconciliationNotes
    ) {}

    public record StatusUpdate(@NotNull String status, String reason) {}

    public record ReconcileRequest(@NotNull @DecimalMin("0.0") BigDecimal negotiatedRate, String notes) {}
}
