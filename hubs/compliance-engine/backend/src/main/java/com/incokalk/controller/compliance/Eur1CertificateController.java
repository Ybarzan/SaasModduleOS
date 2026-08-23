package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.Eur1Certificate;
import com.incokalk.service.Eur1CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

import static com.incokalk.model.CompanyRole.Role.*;

@RestController
@RequestMapping("/v1/eur1")
@RequiredArgsConstructor
@Tag(name = "EUR.1 Certificate", description = "Gestion des certificats EUR.1 pour regimes preferentiels")
@RequiresPlan(Company.Plan.PRO)
public class Eur1CertificateController {

    private final Eur1CertificateService eur1Service;

    @PostMapping
    @RolesAllowed({OWNER, ADMIN, MANAGER})
    @Operation(summary = "Creer un certificat EUR.1")
    public ResponseEntity<Eur1Certificate> create(@Valid @RequestBody Eur1Certificate cert,
                                                  HttpServletRequest request) {
        UUID companyId = (UUID) request.getAttribute("companyId");
        return ResponseEntity.ok(eur1Service.create(companyId, cert));
    }

    @GetMapping
    @RolesAllowed({OWNER, ADMIN, MANAGER, USER})
    @Operation(summary = "Lister les certificats EUR.1")
    public ResponseEntity<?> list(HttpServletRequest request) {
        UUID companyId = (UUID) request.getAttribute("companyId");
        return ResponseEntity.ok(eur1Service.list(companyId));
    }

    @GetMapping("/{id}")
    @RolesAllowed({OWNER, ADMIN, MANAGER, USER})
    @Operation(summary = "Obtenir un certificat EUR.1 par ID")
    public ResponseEntity<Eur1Certificate> get(@PathVariable UUID id, HttpServletRequest request) {
        UUID companyId = (UUID) request.getAttribute("companyId");
        return ResponseEntity.ok(eur1Service.get(companyId, id));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({OWNER, ADMIN, MANAGER})
    @Operation(summary = "Supprimer un certificat EUR.1")
    public ResponseEntity<?> delete(@PathVariable UUID id, HttpServletRequest request) {
        UUID companyId = (UUID) request.getAttribute("companyId");
        eur1Service.delete(companyId, id);
        return ResponseEntity.ok(Map.of("message", "Certificate deleted"));
    }

    @GetMapping("/{id}/validate")
    @RolesAllowed({OWNER, ADMIN, MANAGER, USER})
    @Operation(summary = "Valider un certificat EUR.1 (verifier validite)")
    public ResponseEntity<Eur1CertificateService.CertificateValidation> validate(
            @PathVariable UUID id, HttpServletRequest request) {
        UUID companyId = (UUID) request.getAttribute("companyId");
        return ResponseEntity.ok(eur1Service.validate(companyId, id));
    }
}
