package com.fleethub.security;

import com.fleethub.model.IntegrationApiKey;
import com.fleethub.model.IntegrationConsumer;
import com.fleethub.model.IntegrationConsumer.Scope;
import com.fleethub.service.InternalIntegrationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Point d'entrée unique de l'authentification machine-à-machine des autres hubs
 * sur {@code /api/internal/**} (en-tête {@code X-Internal-Integration-Key}).
 * <p>
 * Avant, ces routes étaient {@code permitAll} et chaque méthode de contrôleur devait
 * penser à valider la clé elle-même : une route ajoutée sans cet appel était publique.
 * Désormais la clé est vérifiée ici pour toute la famille de routes, la société est
 * posée dans {@link TenantContext} (ce qui soumet aussi ces appels au
 * {@link SubscriptionGuardFilter} : un abonnement suspendu gèle aussi ce que voient
 * les autres hubs), et la route n'est servie que si la clé en a le droit
 * ({@link IntegrationConsumer#getScopes()}). SecurityConfig exige en plus le rôle
 * {@code INTEGRATION}, que seul ce filtre accorde.
 */
@Component
@RequiredArgsConstructor
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Integration-Key";
    private static final String PREFIX = "/api/internal/";

    private final InternalIntegrationService internalIntegrationService;

    /** Identité d'un hub consommateur authentifié (principal Spring Security). */
    public record IntegrationPrincipal(Long companyId, IntegrationConsumer consumer) {
        @Override
        public String toString() {
            return "integration:" + consumer.name() + "@company-" + companyId;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            reject(response, 401, "Clé " + HEADER + " manquante");
            return;
        }
        Optional<IntegrationApiKey> key = internalIntegrationService.authenticate(apiKey);
        if (key.isEmpty()) {
            reject(response, 401, "Clé invalide ou intégration désactivée");
            return;
        }
        IntegrationConsumer consumer = key.get().getConsumer();
        Scope required = requiredScope(request);
        if (required == null) {
            reject(response, 404, "Route interne inconnue");
            return;
        }
        if (!consumer.allows(required)) {
            reject(response, 403, "La clé « " + consumer.getLabel() + " » n'a pas accès à cette route");
            return;
        }

        // Remplace toute authentification JWT portée par la même requête : sur ces
        // routes, seule la clé fait foi (et c'est elle qui fixe la société).
        var principal = new IntegrationPrincipal(key.get().getCompany().getId(), consumer);
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_INTEGRATION")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.set(key.get().getCompany());
        filterChain.doFilter(request, response);
    }

    /** Droit exigé par la route ; null pour une route interne non répertoriée. */
    static Scope requiredScope(HttpServletRequest request) {
        String path = request.getRequestURI().substring(PREFIX.length());
        String method = request.getMethod();
        if (path.equals("shipments") || path.startsWith("shipments/")) {
            if ("GET".equals(method)) return Scope.SHIPMENTS_READ;
            if ("POST".equals(method) && path.equals("shipments")) return Scope.SHIPMENTS_CREATE;
            return null;
        }
        if ("GET".equals(method) && path.equals("dashboard-summary")) return Scope.DASHBOARD_READ;
        if ("GET".equals(method) && path.equals("billing-summary")) return Scope.BILLING_READ;
        return null;
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message.replace("\"", "'") + "\"}");
    }
}
