package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.CustomsInvoice;
import com.incokalk.repository.CustomsInvoiceRepository;
import com.incokalk.service.compliance.CustomsInvoiceGeneratorService;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/compliance/customs-invoice")
@RequiredArgsConstructor
@Tag(name = "Customs Invoice", description = "G�n�ration de factures douani�res")
@RequiresPlan(Company.Plan.PRO)
public class CustomsInvoiceController {

    private final CustomsInvoiceGeneratorService customsInvoiceService;
    private final CustomsInvoiceRepository customsInvoiceRepo;

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "G�n�rer une facture douani�re pour une exp�dition")
    public ResponseEntity<CustomsInvoice> generateInvoice(
            @Valid @RequestBody GenerateInvoiceRequest body,
            HttpServletRequest httpReq) {
        UUID companyId = com.incokalk.tenant.TenantContext.get();
        CustomsInvoice invoice = customsInvoiceService.generateInvoice(body.shipmentId(), companyId);
        return ResponseEntity.status(201).body(invoice);
    }

    @GetMapping("/shipment/{shipmentId}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "R�cup�rer la facture douani�re d'une exp�dition")
    public ResponseEntity<CustomsInvoice> getByShipment(
            @PathVariable UUID shipmentId,
            HttpServletRequest httpReq) {
        UUID companyId = com.incokalk.tenant.TenantContext.get();
        return customsInvoiceRepo.findByCompanyIdAndShipmentId(companyId, shipmentId)
                .stream()
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les factures douani�res")
    public ResponseEntity<List<CustomsInvoice>> listInvoices(
            @RequestParam(required = false) String status,
            HttpServletRequest httpReq) {
        UUID companyId = com.incokalk.tenant.TenantContext.get();
        List<CustomsInvoice> invoices = customsInvoiceRepo.findByCompanyIdOrderByInvoiceDateDesc(companyId);
        if (status != null && !status.isBlank()) {
            invoices = invoices.stream()
                    .filter(inv -> inv.getStatus() != null && inv.getStatus().equalsIgnoreCase(status))
                    .toList();
        }
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "D�tail d'une facture douani�re")
    public ResponseEntity<CustomsInvoice> getById(
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        UUID companyId = com.incokalk.tenant.TenantContext.get();
        return customsInvoiceRepo.findByCompanyIdAndId(companyId, id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record GenerateInvoiceRequest(
            @NotNull UUID shipmentId
    ) {}
}