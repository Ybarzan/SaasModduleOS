package com.fleethub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String TOKEN_COOKIE = "fh_token";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenRevocationService tokenRevocationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = null;
        String tokenId = null;
        try {
            username = jwtService.extractUsername(token);
            tokenId = jwtService.extractTokenId(token);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            // Token invalide ou expiré -> considérer comme non authentifié
        }

        if (username != null && tokenId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (tokenRevocationService.isRevoked(tokenId)) {
                filterChain.doFilter(request, response);
                return;
            }

            UserDetails userDetails = null;
            try {
                userDetails = userDetailsService.loadUserByUsername(username);
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                // Utilisateur introuvable -> non authentifié
            }
            if (userDetails != null && userDetails.isEnabled() && jwtService.isTokenValid(token, userDetails)) {
                // Vérifier le cutoff de révocation globale (revoke-all admin)
                if (userDetails instanceof AppUserPrincipal principal) {
                    java.util.Date issuedAt = jwtService.extractIssuedAt(token);
                    if (tokenRevocationService.isRevokedByCutoff(principal.getId(), issuedAt)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Authorization header (mobile / API clients)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // 2. HttpOnly cookie (web browser)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (TOKEN_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
