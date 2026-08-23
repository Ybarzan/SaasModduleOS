package com.incokalk.controller.warehouse;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.Discrepancy;
import com.incokalk.model.ReceivingOrder;
import com.incokalk.model.ReceivingOrderLine;
import com.incokalk.model.ReceivingScan;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ReceivingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/receivings")
@RequiredArgsConstructor
@Tag(name = "Receiving", description = "Réception marchandises & scan code-barres/QR")
@RequiresPlan(Company.Plan.ENTERPRISE)
public class ReceivingController {

    private final ReceivingService receivingService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister les bons de réception (filtres ?status=, ?warehouseId=, ?shipmentId=)")
    public ResponseEntity<List<ReceivingOrder>> list(
            @RequestParam(required = false) ReceivingOrder.Status status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID shipmentId) {
        return ResponseEntity.ok(receivingService.listOrders(status, warehouseId, shipmentId));
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Détail d'un bon de réception (lignes, scans, écarts)")
    public ResponseEntity<ReceivingService.OrderDetail> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(receivingService.getOrder(id));
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Créer un bon de réception (depuis shipment ou à la main)")
    public ResponseEntity<ReceivingOrder> create(@Valid @RequestBody CreateOrder body,
                                                 HttpServletRequest httpReq) {
        UUID userId = extractUserId(httpReq);
        return ResponseEntity.ok(receivingService.createOrder(
                body.warehouseId(), body.shipmentId(), body.reference(), body.notes(),
                body.lines(), userId));
    }

    @PostMapping("/{id}/lines")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Ajouter une ligne attendue au bon de réception")
    public ResponseEntity<ReceivingOrderLine> addLine(@PathVariable UUID id, @Valid @RequestBody AddLine body) {
        return ResponseEntity.ok(receivingService.addLine(id, body.itemId(), body.quantityExpected(), body.unit()));
    }

    @PostMapping("/{id}/scan")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Enregistrer un scan code-barres/QR (reçoit l'article, poste le stock)")
    public ResponseEntity<ReceivingScan> scan(@PathVariable UUID id,
                                              @Valid @RequestBody ReceivingService.ScanRequest body,
                                              HttpServletRequest httpReq) {
        UUID userId = extractUserId(httpReq);
        return ResponseEntity.ok(receivingService.scan(id, body, userId));
    }

    @PostMapping("/{id}/damage")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Signaler de la marchandise endommagée")
    public ResponseEntity<Discrepancy> reportDamage(@PathVariable UUID id, @Valid @RequestBody Damage body,
                                                    HttpServletRequest httpReq) {
        UUID userId = extractUserId(httpReq);
        return ResponseEntity.ok(receivingService.reportDamage(id, body.itemId(), body.quantity(), body.notes(), userId));
    }

    @PostMapping("/{id}/complete")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Clôturer le bon de réception (écarts manquants détectés)")
    public ResponseEntity<ReceivingOrder> complete(@PathVariable UUID id, HttpServletRequest httpReq) {
        UUID userId = extractUserId(httpReq);
        return ResponseEntity.ok(receivingService.complete(id, userId));
    }

    @PostMapping("/{id}/cancel")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Annuler le bon de réception")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        receivingService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    // ── Écarts ─────────────────────────────────────────────────────────

    @GetMapping("/discrepancies")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister tous les écarts de réception")
    public ResponseEntity<List<Discrepancy>> discrepancies() {
        return ResponseEntity.ok(receivingService.getDiscrepancies());
    }

    @PostMapping("/discrepancies/{id}/resolve")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Résoudre un écart")
    public ResponseEntity<Discrepancy> resolveDiscrepancy(@PathVariable UUID id,
                                                          @RequestBody(required = false) Resolve body) {
        return ResponseEntity.ok(receivingService.resolveDiscrepancy(id, body != null ? body.notes() : null));
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new RuntimeException("Non authentifié");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
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
