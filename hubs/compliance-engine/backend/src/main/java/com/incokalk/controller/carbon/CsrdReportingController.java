package com.incokalk.controller.carbon;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CsrdReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/csrd")
@RequiredArgsConstructor
@Tag(name = "CSRD Reporting", description = "Reporting CSRD / EU Taxonomy")
@RequiresPlan(Company.Plan.ENTERPRISE)
public class CsrdReportingController {

    private final CsrdReportingService csrdReportingService;

    @GetMapping("/report")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Rapport CSRD complet")
    public ResponseEntity<Map<String, Object>> getCsrdReport() {
        return ResponseEntity.ok(csrdReportingService.getCsrdReport());
    }
}
