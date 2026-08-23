package com.incokalk.controller.financial;

import com.incokalk.model.CarrierInvoice;
import com.incokalk.model.CarrierInvoice.InvoiceStatus;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CarrierInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/carrier-invoices")
@RequiredArgsConstructor
@Tag(name = "Carrier Invoices", description = "Facturation transporteur (AP)")
@RequiresPlan(Company.Plan.ENTERPRISE)
public class CarrierInvoiceController {

    private final CarrierInvoiceService invoiceService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les factures transporteur")
    public ResponseEntity<?> listAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null && size > 0) {
            Page<CarrierInvoice> result = invoiceService.getAll(PageRequest.of(page, size));
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(invoiceService.getAll());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir une facture transporteur par ID")
    public ResponseEntity<CarrierInvoice> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.getById(id));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer une facture transporteur")
    public ResponseEntity<CarrierInvoice> create(@Valid @RequestBody CreateCarrierInvoice body) {
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
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour le statut d'une facture transporteur")
    public ResponseEntity<CarrierInvoice> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdate body) {
        InvoiceStatus newStatus = InvoiceStatus.valueOf(body.status());
        return ResponseEntity.ok(invoiceService.updateStatus(id, newStatus, body.reason()));
    }

    @PutMapping("/{id}/reconcile")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Réconcilier une facture transporteur")
    public ResponseEntity<CarrierInvoice> reconcile(
            @PathVariable UUID id,
            @Valid @RequestBody ReconcileRequest body) {
        return ResponseEntity.ok(invoiceService.reconcile(id, body.negotiatedRate(), body.notes()));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une facture transporteur")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        invoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Statistiques des factures transporteur")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(invoiceService.getStats());
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
