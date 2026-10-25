package com.finance.controller;

import com.finance.model.InvoiceFinancing;
import com.finance.service.SupplyChainFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/finance")
@RequiredArgsConstructor
@Tag(name = "Supply Chain Finance", description = "Financement de la chaîne d'approvisionnement")
public class SupplyChainFinanceController {

    private final SupplyChainFinanceService supplyChainFinanceService;

    @PostMapping("/request")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Demander un financement de facture")
    public ResponseEntity<InvoiceFinancing> requestFinancing(@Valid @RequestBody FinancingRequest body) {
        return ResponseEntity.ok(supplyChainFinanceService.requestFinancing(body.invoiceId(), body.amount()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approuver une demande de financement")
    public ResponseEntity<InvoiceFinancing> approveFinancing(@PathVariable UUID id) {
        return ResponseEntity.ok(supplyChainFinanceService.approveFinancing(id));
    }

    @PostMapping("/{id}/fund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Financer une demande approuvée")
    public ResponseEntity<InvoiceFinancing> fundFinancing(@PathVariable UUID id) {
        return ResponseEntity.ok(supplyChainFinanceService.fundFinancing(id));
    }

    @PostMapping("/{id}/repay")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rembourser un financement")
    public ResponseEntity<InvoiceFinancing> repayFinancing(@PathVariable UUID id) {
        return ResponseEntity.ok(supplyChainFinanceService.repayFinancing(id));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Historique des financements")
    public ResponseEntity<List<InvoiceFinancing>> getHistory() {
        return ResponseEntity.ok(supplyChainFinanceService.getFinancingHistory());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Statistiques de financement")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(supplyChainFinanceService.getStats());
    }

    @GetMapping("/early-payment-discount")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Calculer l'escompte pour paiement anticipé")
    public ResponseEntity<Map<String, Object>> getEarlyPaymentDiscount(
            @RequestParam UUID invoiceId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(supplyChainFinanceService.getEarlyPaymentDiscount(invoiceId, amount));
    }

    public record FinancingRequest(
            @NotNull UUID invoiceId,
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {}
}
