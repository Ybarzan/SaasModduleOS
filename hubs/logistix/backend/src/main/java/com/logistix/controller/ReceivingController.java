package com.logistix.controller;

import com.logistix.model.Discrepancy;
import com.logistix.model.ReceivingOrder;
import com.logistix.model.ReceivingOrderLine;
import com.logistix.model.ReceivingScan;
import com.logistix.security.UserPrincipal;
import com.logistix.service.ReceivingService;
import com.logistix.service.integration.fleethub.ShipmentSearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/receivings")
@RequiredArgsConstructor
@Tag(name = "Receiving", description = "Réception marchandises & scan code-barres/QR")
public class ReceivingController {

    private final ReceivingService receivingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Lister les bons de réception (filtres ?status=, ?warehouseId=, ?shipmentId=)")
    public ResponseEntity<List<ReceivingOrder>> list(
            @RequestParam(required = false) ReceivingOrder.Status status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID shipmentId) {
        return ResponseEntity.ok(receivingService.listOrders(status, warehouseId, shipmentId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Détail d'un bon de réception (lignes, scans, écarts)")
    public ResponseEntity<ReceivingService.OrderDetail> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(receivingService.getOrder(id));
    }

    @GetMapping("/shipments/search")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Recherche d'expéditions fleet-hub par texte (numéro de commande ou destinataire)")
    public ResponseEntity<List<ShipmentSearchResult>> searchShipments(@RequestParam("q") String q) {
        return ResponseEntity.ok(receivingService.searchShipments(q));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Créer un bon de réception (lignes fournies explicitement)")
    public ResponseEntity<ReceivingOrder> create(@Valid @RequestBody CreateOrder body,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(receivingService.createOrder(
                body.warehouseId(), body.shipmentId(), body.reference(), body.notes(),
                body.lines(), principal.getId()));
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Ajouter une ligne attendue au bon de réception")
    public ResponseEntity<ReceivingOrderLine> addLine(@PathVariable UUID id, @Valid @RequestBody AddLine body) {
        return ResponseEntity.ok(receivingService.addLine(id, body.itemId(), body.quantityExpected(), body.unit()));
    }

    @PostMapping("/{id}/scan")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Enregistrer un scan code-barres/QR (reçoit l'article, poste le stock)")
    public ResponseEntity<ReceivingScan> scan(@PathVariable UUID id,
                                              @Valid @RequestBody ReceivingService.ScanRequest body,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(receivingService.scan(id, body, principal.getId()));
    }

    @PostMapping("/{id}/damage")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Signaler de la marchandise endommagée")
    public ResponseEntity<Discrepancy> reportDamage(@PathVariable UUID id, @Valid @RequestBody Damage body,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(receivingService.reportDamage(id, body.itemId(), body.quantity(), body.notes(), principal.getId()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Clôturer le bon de réception (écarts manquants détectés)")
    public ResponseEntity<ReceivingOrder> complete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(receivingService.complete(id, principal.getId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Annuler le bon de réception")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        receivingService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    // ── Écarts ─────────────────────────────────────────────────────────

    @GetMapping("/discrepancies")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Lister tous les écarts de réception")
    public ResponseEntity<List<Discrepancy>> discrepancies() {
        return ResponseEntity.ok(receivingService.getDiscrepancies());
    }

    @PostMapping("/discrepancies/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Résoudre un écart")
    public ResponseEntity<Discrepancy> resolveDiscrepancy(@PathVariable UUID id,
                                                          @RequestBody(required = false) Resolve body) {
        return ResponseEntity.ok(receivingService.resolveDiscrepancy(id, body != null ? body.notes() : null));
    }

    public record CreateOrder(
            @NotNull UUID warehouseId,
            UUID shipmentId,
            String reference,
            String notes,
            List<ReceivingService.CreateLine> lines
    ) {}

    public record AddLine(
            @NotNull UUID itemId,
            @NotNull @DecimalMin(value = "0.0", message = "La quantité attendue ne peut pas être négative") BigDecimal quantityExpected,
            String unit
    ) {}

    public record Damage(
            @NotNull UUID itemId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "La quantité endommagée doit être positive") BigDecimal quantity,
            String notes
    ) {}

    public record Resolve(String notes) {}
}
