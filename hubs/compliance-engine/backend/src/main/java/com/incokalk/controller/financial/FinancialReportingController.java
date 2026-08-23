package com.incokalk.controller.financial;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.ShipmentFinancials;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.FinancialReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/financials")
@RequiredArgsConstructor
@Tag(name = "Financial Reporting", description = "Reporting financier et P&L")
@RequiresPlan(Company.Plan.ENTERPRISE)
public class FinancialReportingController {

    private final FinancialReportingService financialReportingService;

    record CreateShipmentFinancials(
            String shipmentId,
            String clientName,
            String origin,
            String destination,
            String mode,
            String carrierName,
            BigDecimal revenue,
            String revenueCurrency,
            BigDecimal costFreight,
            BigDecimal costFuel,
            BigDecimal costHandling,
            BigDecimal costCustoms,
            BigDecimal costInsurance,
            BigDecimal costWarehouse,
            BigDecimal costLastMile,
            BigDecimal costOther
    ) {}

    @GetMapping("/shipments")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Lister les données financières des expéditions")
    public ResponseEntity<List<ShipmentFinancials>> listAll() {
        return ResponseEntity.ok(financialReportingService.getAllShipments());
    }

    @GetMapping("/shipments/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Détail financier d'une expédition")
    public ResponseEntity<ShipmentFinancials> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(financialReportingService.getShipmentById(id));
    }

    @PostMapping("/shipments")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer les données financières d'une expédition")
    public ResponseEntity<ShipmentFinancials> create(@Valid @RequestBody CreateShipmentFinancials request) {
        ShipmentFinancials financials = ShipmentFinancials.builder()
                .clientName(request.clientName())
                .origin(request.origin())
                .destination(request.destination())
                .mode(request.mode())
                .carrierName(request.carrierName())
                .revenue(request.revenue())
                .revenueCurrency(request.revenueCurrency() != null ? request.revenueCurrency() : "EUR")
                .costFreight(request.costFreight())
                .costFuel(request.costFuel())
                .costHandling(request.costHandling())
                .costCustoms(request.costCustoms())
                .costInsurance(request.costInsurance())
                .costWarehouse(request.costWarehouse())
                .costLastMile(request.costLastMile())
                .costOther(request.costOther())
                .build();
        return ResponseEntity.ok(financialReportingService.create(financials));
    }

    @PutMapping("/shipments/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour les données financières")
    public ResponseEntity<ShipmentFinancials> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateShipmentFinancials request) {
        ShipmentFinancials financials = ShipmentFinancials.builder()
                .clientName(request.clientName())
                .origin(request.origin())
                .destination(request.destination())
                .mode(request.mode())
                .carrierName(request.carrierName())
                .revenue(request.revenue())
                .revenueCurrency(request.revenueCurrency())
                .costFreight(request.costFreight())
                .costFuel(request.costFuel())
                .costHandling(request.costHandling())
                .costCustoms(request.costCustoms())
                .costInsurance(request.costInsurance())
                .costWarehouse(request.costWarehouse())
                .costLastMile(request.costLastMile())
                .costOther(request.costOther())
                .build();
        return ResponseEntity.ok(financialReportingService.update(id, financials));
    }

    @DeleteMapping("/shipments/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer les données financières")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        financialReportingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboard")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Tableau de bord financier")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(financialReportingService.getDashboard());
    }

    @GetMapping("/by-carrier")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Profit par transporteur")
    public ResponseEntity<List<Map<String, Object>>> getProfitByCarrier() {
        return ResponseEntity.ok(financialReportingService.getProfitByCarrier());
    }

    @GetMapping("/by-lane")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Profit par lane")
    public ResponseEntity<List<Map<String, Object>>> getProfitByLane() {
        return ResponseEntity.ok(financialReportingService.getProfitByLane());
    }

    @GetMapping("/top-lanes")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Top lanes par marge")
    public ResponseEntity<List<Map<String, Object>>> getTopLanes(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(financialReportingService.getTopLanes(limit));
    }

    @GetMapping("/top-carriers")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Top transporteurs par marge")
    public ResponseEntity<List<Map<String, Object>>> getTopCarriers(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(financialReportingService.getTopCarriers(limit));
    }
}
