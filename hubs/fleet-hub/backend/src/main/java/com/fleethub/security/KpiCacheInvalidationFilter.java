package com.fleethub.security;

import com.fleethub.config.TenantCacheEvictor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Après une écriture réussie d'un utilisateur d'une société (POST / PUT / PATCH /
 * DELETE sur /api/**, réponse 2xx), invalide le cache des KPI et du tableau de bord
 * de cette société : la saisie (trajet, plein, véhicule, import…) est visible tout
 * de suite. Volontairement large — toute écriture compte — plutôt que de lister les
 * données dont dépend chaque indicateur et d'en oublier une.
 * <p>
 * Placé après TenantFilter / InternalApiKeyFilter : la société est alors connue,
 * et encore présente dans {@link TenantContext} au retour de la chaîne.
 */
@Component
@RequiredArgsConstructor
public class KpiCacheInvalidationFilter extends OncePerRequestFilter {

    private final TenantCacheEvictor evictor;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())
                || "OPTIONS".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        int status = response.getStatus();
        if (status >= 200 && status < 300) {
            evictor.evictCompany(TenantContext.companyId());
        }
    }
}
