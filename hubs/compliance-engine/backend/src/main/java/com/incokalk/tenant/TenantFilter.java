package com.incokalk.tenant;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.CompanyRoleRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    private final CompanyRepository companyRepository;
    private final CompanyRoleRepository companyRoleRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                   HttpServletResponse res,
                                   FilterChain chain) throws ServletException, IOException {
        try {
            extractTenantId(req).ifPresent(id -> {
                TenantContext.set(id);
                req.setAttribute("companyId", id);
            });
            chain.doFilter(req, res);
        } finally {
            TenantContext.clear();
        }
    }

    private Optional<UUID> extractTenantId(HttpServletRequest req) {
        String header = req.getHeader("X-Tenant-ID");
        UUID headerTenant = null;
        if (header != null) {
            try {
                headerTenant = UUID.fromString(header);
            } catch (IllegalArgumentException ignored) {
            }
        }

        String host = req.getServerName();
        UUID hostTenant = null;
        if (host != null && host.contains(".")) {
            String slug = host.substring(0, host.indexOf('.'));
            if (!slug.equals("www") && !slug.equals("localhost")) {
                hostTenant = companyRepository.findBySlug(slug).map(Company::getId).orElse(null);
            }
        }

        UUID tenantId = headerTenant != null ? headerTenant : hostTenant;
        if (tenantId == null) return Optional.empty();

        Object userIdAttr = req.getAttribute("userId");
        if (userIdAttr == null) {
            log.debug("[Tenant] Pas d'utilisateur authentifié, tenant={}", tenantId);
            return Optional.of(tenantId);
        }

        UUID userId = userIdAttr instanceof UUID u ? u : UUID.fromString(userIdAttr.toString());
        boolean belongs = companyRoleRepository.findByCompanyIdAndUserId(tenantId, userId).isPresent();
        if (!belongs) {
            log.warn("[Security] Tentative d'accès au tenant {} par l'utilisateur {} qui n'en est pas membre", tenantId, userId);
            return Optional.empty();
        }

        return Optional.of(tenantId);
    }
}
