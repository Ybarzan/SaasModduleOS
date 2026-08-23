package com.fleethub.controller;

import com.fleethub.billing.StripeProperties;
import com.fleethub.billing.StripeService;
import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.BillingStatusDto;
import com.fleethub.dto.CheckoutResponse;
import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Facturation de la société courante (checkout, portail, statut, changement de plan).
 * Accessible aux rôles tenant (ADMIN / GESTIONNAIRE).
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
@Tag(name = "Facturation", description = "Gestion de la facturation et des abonnements Stripe")
public class BillingController {

    private final StripeService stripeService;
    private final CompanyRepository companyRepository;
    private final StripeProperties stripeProperties;

    @GetMapping("/status")
    @Operation(summary = "Statut de facturation", description = "Retourne le statut d'abonnement et les détails de la formule actuelle")
    @ApiResponse(responseCode = "200", description = "Statut retourné avec succès")
    public BillingStatusDto status() {
        Company c = currentCompany();
        return new BillingStatusDto(
                c.getPlan().name(),
                c.getStatus().name(),
                c.getTrialEndsAt(),
                c.getSubscriptionProvider(),
                c.getSubscriptionId(),
                c.getPlan().getMaxVehicles(),
                c.getPlan().getMaxDrivers(),
                stripeProperties.isEnabled());
    }

    @PostMapping("/checkout")
    @Operation(summary = "Créer une session de paiement", description = "Initie une session Stripe Checkout pour souscrire ou changer de plan")
    @ApiResponse(responseCode = "200", description = "Session de paiement créée avec succès")
    @ApiResponse(responseCode = "409", description = "Déjà abonné à ce plan")
    @ApiResponse(responseCode = "400", description = "Le plan TRIAL n'est pas souscriptible en ligne")
    public CheckoutResponse checkout(@RequestBody CheckoutRequest body) {
        Company c = currentCompany();
        if (c.getStatus() == Company.CompanyStatus.ACTIVE
                && c.getPlan() == body.plan()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vous êtes déjà abonné au plan " + body.plan());
        }
        if (body.plan() == Company.SubscriptionPlan.TRIAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le plan TRIAL n'est pas souscriptible en ligne.");
        }
        String url = stripeService.createCheckoutSession(c, body.plan());
        return new CheckoutResponse(url);
    }

    @PostMapping("/portal")
    @Operation(summary = "Portail client Stripe", description = "Crée une session d'accès au portail de gestion de l'abonnement Stripe")
    @ApiResponse(responseCode = "200", description = "Session du portail créée avec succès")
    public CheckoutResponse portal() {
        return new CheckoutResponse(stripeService.createPortalSession(currentCompany()));
    }

    @PostMapping("/plan")
    @Operation(summary = "Changer de plan", description = "Change la formule d'abonnement de la société via Stripe")
    @ApiResponse(responseCode = "200", description = "Plan changé avec succès")
    @ApiResponse(responseCode = "400", description = "Plan invalide")
    public BillingStatusDto changePlan(@RequestBody CheckoutRequest body) {
        Company c = currentCompany();
        stripeService.changePlan(c, body.plan());
        c.setPlan(body.plan());
        companyRepository.save(c);
        return status();
    }

    private Company currentCompany() {
        Long companyId = TenantContext.companyId();
        if (companyId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cette opération nécessite une société active.");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Société introuvable"));
    }

    public record CheckoutRequest(@NotNull Company.SubscriptionPlan plan) {}
}
