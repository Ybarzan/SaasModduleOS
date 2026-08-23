package com.incokalk.controller.financial;

import com.incokalk.model.CompanyRole;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.BillingService;
import com.stripe.exception.StripeException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/plans")
    public ResponseEntity<List<Map<String, Object>>> getPlans() {
        return ResponseEntity.ok(billingService.getPlans());
    }

    @GetMapping("/subscription")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    public ResponseEntity<Map<String, Object>> getSubscription(HttpServletRequest request) {
        UUID companyId = (UUID) request.getAttribute("companyId");
        return ResponseEntity.ok(billingService.getSubscription(companyId));
    }

    @PostMapping("/checkout")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    public ResponseEntity<Map<String, String>> createCheckoutSession(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) throws StripeException {
        UUID companyId = (UUID) request.getAttribute("companyId");
        UUID userId = (UUID) request.getAttribute("userId");
        String planId = body.get("planId");
        String billingCycle = body.getOrDefault("billingCycle", "monthly");

        Map<String, String> result = billingService.createCheckoutSession(companyId, userId, planId, billingCycle);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/portal")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    public ResponseEntity<Map<String, String>> createPortalSession(HttpServletRequest request) throws StripeException {
        UUID companyId = (UUID) request.getAttribute("companyId");
        return ResponseEntity.ok(billingService.createPortalSession(companyId));
    }

    @GetMapping("/invoices")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    public ResponseEntity<List<Map<String, Object>>> getInvoices(HttpServletRequest request) {
        UUID companyId = (UUID) request.getAttribute("companyId");
        return ResponseEntity.ok(billingService.getInvoices(companyId));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getBillingStatus(HttpServletRequest request) {
        UUID companyId = (UUID) request.getAttribute("companyId");
        Map<String, Object> sub = billingService.getSubscription(companyId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("stripeConfigured", billingService.isStripeConfigured());
        result.put("subscription", sub);
        return ResponseEntity.ok(result);
    }
}
