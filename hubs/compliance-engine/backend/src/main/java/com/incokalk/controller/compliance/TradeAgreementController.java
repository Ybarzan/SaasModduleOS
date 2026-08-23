package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.TradeAgreement;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.CustomsDutyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/trade-agreements")
@RequiredArgsConstructor
@Tag(name = "Trade Agreements", description = "Accords commerciaux et régimes préférentiels")
@RequiresPlan(Company.Plan.STARTER)
public class TradeAgreementController {

    private final CustomsDutyService customsDutyService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister tous les accords commerciaux actifs")
    public ResponseEntity<List<TradeAgreement>> list() {
        return ResponseEntity.ok(customsDutyService.findActiveAgreements());
    }

    @GetMapping("/{code}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Obtenir un accord par son code")
    public ResponseEntity<?> getByCode(@PathVariable String code) {
        return customsDutyService.findAgreementByCode(code)
            .map(a -> ResponseEntity.ok((Object) a))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-country/{countryCode}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister les accords pour un pays donné")
    public ResponseEntity<List<TradeAgreement>> getByCountry(@PathVariable String countryCode) {
        return ResponseEntity.ok(customsDutyService.findAgreementsByCountry(countryCode));
    }

    @GetMapping("/by-chapter/{chapter}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Lister les accords couvrant un chapitre HS")
    public ResponseEntity<List<TradeAgreement>> getByChapter(@PathVariable String chapter) {
        return ResponseEntity.ok(customsDutyService.findAgreementsByChapter(chapter));
    }
}
