package com.incokalk.controller.financial;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.PaymentTerm;
import com.incokalk.security.RequiresPlan;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.PaymentTermService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/payment-terms")
@RequiredArgsConstructor
@Tag(name = "Payment Terms", description = "Conditions de paiement")
@RequiresPlan(Company.Plan.ENTERPRISE)
public class PaymentTermController {

    private final PaymentTermService paymentTermService;

    @GetMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Lister les conditions de paiement")
    public ResponseEntity<List<PaymentTerm>> listAll() {
        return ResponseEntity.ok(paymentTermService.getAll());
    }

    @GetMapping("/default")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN, CompanyRole.Role.MANAGER})
    @Operation(summary = "Obtenir la condition de paiement par défaut")
    public ResponseEntity<PaymentTerm> getDefault() {
        PaymentTerm term = paymentTermService.getDefault();
        return term != null ? ResponseEntity.ok(term) : ResponseEntity.noContent().build();
    }

    @PostMapping
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer une condition de paiement")
    public ResponseEntity<PaymentTerm> create(@Valid @RequestBody CreatePaymentTerm body) {
        PaymentTerm term = PaymentTerm.builder()
                .name(body.name())
                .code(body.code())
                .description(body.description())
                .daysUntilDue(body.daysUntilDue())
                .earlyPaymentDiscountPercent(body.earlyPaymentDiscountPercent())
                .earlyPaymentDiscountDays(body.earlyPaymentDiscountDays())
                .lateFeePercent(body.lateFeePercent())
                .isActive(body.active() != null ? body.active() : true)
                .isDefault(body.isDefault() != null ? body.isDefault() : false)
                .build();
        return ResponseEntity.ok(paymentTermService.create(term));
    }

    @PutMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour une condition de paiement")
    public ResponseEntity<PaymentTerm> update(@PathVariable UUID id, @Valid @RequestBody CreatePaymentTerm body) {
        PaymentTerm term = PaymentTerm.builder()
                .name(body.name())
                .code(body.code())
                .description(body.description())
                .daysUntilDue(body.daysUntilDue())
                .earlyPaymentDiscountPercent(body.earlyPaymentDiscountPercent())
                .earlyPaymentDiscountDays(body.earlyPaymentDiscountDays())
                .lateFeePercent(body.lateFeePercent())
                .isActive(body.active() != null ? body.active() : true)
                .isDefault(body.isDefault() != null ? body.isDefault() : false)
                .build();
        return ResponseEntity.ok(paymentTermService.update(id, term));
    }

    @DeleteMapping("/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Supprimer une condition de paiement")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        paymentTermService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/seed")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Initialiser les conditions de paiement par défaut")
    public ResponseEntity<Map<String, String>> seed() {
        paymentTermService.seedDefaults();
        return ResponseEntity.ok(Map.of("message", "Conditions de paiement par défaut créées"));
    }

    public record CreatePaymentTerm(
            @NotBlank String name,
            @NotBlank String code,
            String description,
            @NotNull @Min(0) Integer daysUntilDue,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal earlyPaymentDiscountPercent,
            @Min(0) Integer earlyPaymentDiscountDays,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal lateFeePercent,
            Boolean active,
            Boolean isDefault
    ) {}
}
