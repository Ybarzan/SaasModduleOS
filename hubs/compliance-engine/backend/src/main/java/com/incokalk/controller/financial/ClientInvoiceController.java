package com.incokalk.controller.financial;

import com.incokalk.model.ClientInvoice;
import com.incokalk.model.ClientInvoice.InvoiceStatus;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ClientInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/client-invoices")
@RequiredArgsConstructor
@Tag(name = "Client Invoices", description = "Facturation client (Accounts Receivable)")
@RequiresPlan(Company.Plan.ENTERPRISE)
public class ClientInvoiceController {

    private final ClientInvoiceService clientInvoiceService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les factures clients")
    public ResponseEntity<List<ClientInvoice>> listAll() {
        return ResponseEntity.ok(clientInvoiceService.getAll());
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir une facture client par ID")
    public ResponseEntity<ClientInvoice> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(clientInvoiceService.getById(id));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer une facture client")
    public ResponseEntity<ClientInvoice> create(@Valid @RequestBody CreateClientInvoice body) {
        ClientInvoice invoice = ClientInvoice.builder()
                .invoiceNumber(body.invoiceNumber())
                .invoiceDate(body.invoiceDate())
                .dueDate(body.dueDate())
                .clientName(body.clientName())
                .clientEmail(body.clientEmail())
                .subtotal(body.subtotal())
                .vatAmount(body.vatAmount())
                .totalAmount(body.totalAmount())
                .currency(body.currency())
                .earlyPaymentDiscountAmount(body.earlyPaymentDiscountAmount())
                .earlyPaymentDiscountDeadline(body.earlyPaymentDiscountDeadline())
                .notes(body.notes())
                .build();
        return ResponseEntity.ok(clientInvoiceService.create(invoice));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour une facture client")
    public ResponseEntity<ClientInvoice> update(@PathVariable UUID id, @Valid @RequestBody CreateClientInvoice body) {
        ClientInvoice invoice = ClientInvoice.builder()
                .invoiceNumber(body.invoiceNumber())
                .invoiceDate(body.invoiceDate())
                .dueDate(body.dueDate())
                .clientName(body.clientName())
                .clientEmail(body.clientEmail())
                .subtotal(body.subtotal())
                .vatAmount(body.vatAmount())
                .totalAmount(body.totalAmount())
                .currency(body.currency())
                .earlyPaymentDiscountAmount(body.earlyPaymentDiscountAmount())
                .earlyPaymentDiscountDeadline(body.earlyPaymentDiscountDeadline())
                .notes(body.notes())
                .build();
        return ResponseEntity.ok(clientInvoiceService.update(id, invoice));
    }

    @PutMapping("/{id}/status")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour le statut d'une facture client")
    public ResponseEntity<ClientInvoice> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdate body) {
        InvoiceStatus newStatus = InvoiceStatus.valueOf(body.status());
        return ResponseEntity.ok(clientInvoiceService.updateStatus(id, newStatus));
    }

    @PostMapping("/{id}/payment")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Enregistrer un paiement client")
    public ResponseEntity<ClientInvoice> recordPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentRequest body) {
        return ResponseEntity.ok(clientInvoiceService.recordPayment(id, body.amount(), body.reference()));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une facture client")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        clientInvoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Statistiques des factures clients")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(clientInvoiceService.getStats());
    }

    @GetMapping("/overdue")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Factures en retard")
    public ResponseEntity<List<ClientInvoice>> getOverdue() {
        return ResponseEntity.ok(clientInvoiceService.getOverdue());
    }

    @GetMapping("/dashboard")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Tableau de bord facturation")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(clientInvoiceService.getDashboard());
    }

    public record CreateClientInvoice(
            @NotBlank String invoiceNumber,
            @NotNull LocalDate invoiceDate,
            @NotNull LocalDate dueDate,
            @NotBlank String clientName,
            @Email @NotBlank String clientEmail,
            @NotNull @DecimalMin("0.00") BigDecimal subtotal,
            BigDecimal vatAmount,
            @NotNull @DecimalMin("0.00") BigDecimal totalAmount,
            @NotBlank String currency,
            BigDecimal earlyPaymentDiscountAmount,
            LocalDate earlyPaymentDiscountDeadline,
            String notes
    ) {}

    public record StatusUpdate(@NotBlank String status) {}

    public record PaymentRequest(@NotNull @DecimalMin("0.00") BigDecimal amount, @NotBlank String reference) {}
}
