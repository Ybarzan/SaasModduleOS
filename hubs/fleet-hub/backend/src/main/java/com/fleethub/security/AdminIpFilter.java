package com.fleethub.security;

import com.fleethub.model.AdminIpAllowlist;
import com.fleethub.repository.AdminIpAllowlistRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Restreint l'accès aux endpoints admin (/api/admin/**) à un ensemble
 * d'adresses IP autorisées. Désactivé si la liste est vide (dev/test).
 * Vérifie à la fois les IPs du fichier .env et celles en base (AdminIpAllowlist).
 */
@Component
@Slf4j
public class AdminIpFilter extends OncePerRequestFilter {

    private final Set<String> envAllowedIps;
    private final AdminIpAllowlistRepository ipAllowlistRepository;

    public AdminIpFilter(
            @Value("${app.security.admin-allowed-ips:}") String allowedIpsCsv,
            AdminIpAllowlistRepository ipAllowlistRepository) {
        this.ipAllowlistRepository = ipAllowlistRepository;
        this.envAllowedIps = allowedIpsCsv == null || allowedIpsCsv.isBlank()
                ? Set.of()
                : Set.of(allowedIpsCsv.split(",")).stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (envAllowedIps.isEmpty() && ipAllowlistRepository.count() == 0) return true;
        return !request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = clientIp(request);
        if (!isAllowed(ip)) {
            log.warn("Admin IP refusé : {} sur {}", ip, request.getRequestURI());
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"message\":\"Accès admin non autorisé depuis cette adresse IP\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(String ip) {
        if (envAllowedIps.contains(ip)) return true;
        return ipAllowlistRepository.existsByIpAddress(ip);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
