package com.fleethub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Authentifie les requêtes du portail client ({@code /api/client/**}) à
 * partir d'un token CLIENT émis par {@link ClientJwtService}. Totalement
 * indépendant du filtre staff {@link JwtAuthFilter} : un token client porte
 * l'autorité {@code ROLE_CLIENT} uniquement, jamais ADMIN/GESTIONNAIRE/CHAUFFEUR,
 * et ne touche jamais {@link TenantContext} (réservé aux comptes {@code AppUser}).
 * Le clientId/companyId résolus sont posés en attributs de requête, lus
 * directement par les contrôleurs du portail — même convention que côté
 * compliance-engine d'origine.
 */
@Component
@RequiredArgsConstructor
public class ClientAuthFilter extends OncePerRequestFilter {

    private static final String CLIENT_ATTR = "fh.clientId";
    private static final String CLIENT_COMPANY_ATTR = "fh.clientCompanyId";

    private final ClientJwtService clientJwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = extractBearerToken(request);
            if (token != null && clientJwtService.isValid(token)) {
                try {
                    UUID clientId = clientJwtService.extractClientId(token);
                    Long companyId = clientJwtService.extractCompanyId(token);
                    request.setAttribute(CLIENT_ATTR, clientId);
                    request.setAttribute(CLIENT_COMPANY_ATTR, companyId);

                    var authToken = new UsernamePasswordAuthenticationToken(
                            clientId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } catch (Exception ignored) {
                    // token CLIENT malformé -> requête traitée comme non authentifiée
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    public static UUID clientId(HttpServletRequest request) {
        Object v = request.getAttribute(CLIENT_ATTR);
        return v instanceof UUID u ? u : null;
    }

    public static Long clientCompanyId(HttpServletRequest request) {
        Object v = request.getAttribute(CLIENT_COMPANY_ATTR);
        return v instanceof Long l ? l : null;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
