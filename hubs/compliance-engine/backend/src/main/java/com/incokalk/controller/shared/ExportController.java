package com.incokalk.controller.shared;

import com.incokalk.model.CompanyRole;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.ExportService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/v1/export")
@RequiredArgsConstructor
@Tag(name = "Export", description = "Export de données en CSV")
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/shipments")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Exporter les expéditions en CSV")
    public void exportShipments(HttpServletResponse response) throws IOException {
        UUID companyId = TenantContext.get();
        exportService.exportShipmentsCsv(companyId, response);
    }

    @GetMapping("/carriers")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Exporter les transporteurs en CSV")
    public void exportCarriers(HttpServletResponse response) throws IOException {
        UUID companyId = TenantContext.get();
        exportService.exportCarriersCsv(companyId, response);
    }
}
