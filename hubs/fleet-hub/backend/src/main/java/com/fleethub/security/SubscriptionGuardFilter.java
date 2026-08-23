package com.fleethub.security;

import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gèle les données métier d'un tenant dont l'abonnement n'est plus actif
 * (essai expiré ou compte suspendu). Le tenant peut toujours se connecter,
 * souscrire ou payer : les routes de facturation et les droits RGPD restent
 * accessibles. Les autres routes renvoient 402 Payment Required.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionGuardFilter extends OncePerRequestFilter {

    private static final String FROZEN_MESSAGE =
            "Votre abonnement n'est plus actif. Rendez-vous dans la page Abonnement "
                    + "pour souscrire ou régulariser votre paiement.";

    private final CompanyRepository companyRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long companyId = TenantContext.companyId();
        if (companyId != null) {
            Company company = companyRepository.findById(companyId).orElse(null);
            if (company != null
                    && !company.hasActiveSubscription()
                    && !isAllowedPath(request.getRequestURI())) {
                response.setStatus(402);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"" + FROZEN_MESSAGE + "\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowedPath(String uri) {
        return uri.startsWith("/api/billing")
                || uri.startsWith("/api/account")
                || uri.startsWith("/api/auth")
                || uri.startsWith("/api/legal")
                || uri.startsWith("/api/webhooks")
                || uri.startsWith("/api/admin")
                || uri.startsWith("/actuator")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/h2-console");
    }
}
