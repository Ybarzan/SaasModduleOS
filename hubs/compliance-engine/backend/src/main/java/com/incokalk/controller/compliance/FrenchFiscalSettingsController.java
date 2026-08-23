package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.FrenchFiscalConfig;
import com.incokalk.repository.FrenchFiscalConfigRepository;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/fiscal")
@RequiredArgsConstructor
@Tag(name = "French Fiscal Settings", description = "Paramétrage TVA / DEB / Intrastat par entreprise")
@RequiresPlan(Company.Plan.PRO)
public class FrenchFiscalSettingsController {

    private final FrenchFiscalConfigRepository repo;

    @GetMapping("/french-settings")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER, CompanyRole.Role.USER})
    @Operation(summary = "Obtenir la configuration fiscale française de l'entreprise")
    public ResponseEntity<Map<String, Object>> get() {
        UUID companyId = TenantContext.get();
        FrenchFiscalConfig cfg = repo.findByCompanyId(companyId)
            .orElseGet(() -> FrenchFiscalConfig.builder().companyId(companyId).build());
        return ResponseEntity.ok(toResponse(cfg));
    }

    @PostMapping("/french-settings")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Mettre à jour une section (vat, deb, intrastat) de la configuration fiscale")
    @Transactional
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> payload) {
        UUID companyId = TenantContext.get();
        FrenchFiscalConfig cfg = repo.findByCompanyId(companyId)
            .orElseGet(() -> FrenchFiscalConfig.builder().companyId(companyId).build());

        String section = (String) payload.get("section");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.get("data");

        if (data != null) {
            switch (section == null ? "" : section) {
                case "vat" -> {
                    if (data.get("tvaRate") != null) cfg.setVatRate(new BigDecimal(data.get("tvaRate").toString()));
                    if (data.get("vatNumber") != null) cfg.setVatNumber((String) data.get("vatNumber"));
                    if (data.get("intraEuScheme") != null) cfg.setIntraEuScheme((String) data.get("intraEuScheme"));
                }
                case "deb" -> {
                    if (data.get("frequency") != null) cfg.setDebFrequency((String) data.get("frequency"));
                    if (data.get("threshold") != null) cfg.setDebThreshold(new BigDecimal(data.get("threshold").toString()));
                }
                case "intrastat" -> {
                    if (data.get("dispatchThreshold") != null) cfg.setIntrastatDispatchThreshold(new BigDecimal(data.get("dispatchThreshold").toString()));
                    if (data.get("arrivalThreshold") != null) cfg.setIntrastatArrivalThreshold(new BigDecimal(data.get("arrivalThreshold").toString()));
                    if (data.get("declarationType") != null) cfg.setIntrastatDeclarationType((String) data.get("declarationType"));
                }
                default -> { }
            }
        }

        cfg = repo.save(cfg);
        return ResponseEntity.ok(toResponse(cfg));
    }

    private Map<String, Object> toResponse(FrenchFiscalConfig cfg) {
        return Map.of(
            "vat", Map.of(
                "tvaRate", cfg.getVatRate(),
                "vatNumber", cfg.getVatNumber() != null ? cfg.getVatNumber() : "",
                "intraEuScheme", cfg.getIntraEuScheme()
            ),
            "deb", Map.of(
                "frequency", cfg.getDebFrequency(),
                "threshold", cfg.getDebThreshold()
            ),
            "intrastat", Map.of(
                "dispatchThreshold", cfg.getIntrastatDispatchThreshold(),
                "arrivalThreshold", cfg.getIntrastatArrivalThreshold(),
                "declarationType", cfg.getIntrastatDeclarationType()
            )
        );
    }
}
