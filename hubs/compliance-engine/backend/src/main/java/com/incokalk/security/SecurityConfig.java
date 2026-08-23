package com.incokalk.security;

import com.incokalk.model.User;
import com.incokalk.repository.UserRepository;
import com.incokalk.service.ApiKeyService;
import com.incokalk.tenant.TenantContext;
import com.incokalk.tenant.TenantFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyService apiKeyService;
    private final JwtService jwtService;
    private final TenantFilter tenantFilter;
    private final UserRepository userRepository;
    private final Environment environment;
    private final RateLimitFilter rateLimitFilter;

  private static final String[] PUBLIC = {
    "/v1/simulate", "/v1/simulate/incoterms", "/v1/simulate/incoterms/**",
    "/v1/logistics/**",
    "/v1/auth/register", "/v1/auth/login",
    "/v1/auth/forgot-password", "/v1/auth/reset-password", "/v1/auth/verify-email",
    "/v1/auth/refresh",
    "/v1/client/auth/login",
    "/v1/shared/**",
    "/v1/landed-costs/public/**",
    "/v1/tracking/lookup",
    "/v1/webhooks/**",
    "/v1/files/download/**",
    "/v1/billing/plans",
    "/v1/currencies", "/v1/currencies/rates",
    "/incoterms", "/incoterms/**",
    "/actuator/health"
  };

  private static final String[] PUBLIC_DEV_ONLY = {
    "/h2-console/**",
    "/swagger-ui/**", "/v3/api-docs/**"
  };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        String[] publicEndpoints = isDev ? PUBLIC : Arrays.stream(PUBLIC)
            .filter(p -> !Arrays.asList(PUBLIC_DEV_ONLY).contains(p))
            .toArray(String[]::new);

        http
            .csrf(AbstractHttpConfigurer::disable)
            .headers(h -> {
                if (isDev) {
                    h.frameOptions(f -> f.disable());
                } else {
                    h.contentTypeOptions(t -> {});
                    h.referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                }
            })
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, authEx) -> {
                res.setStatus(HttpStatus.UNAUTHORIZED.value());
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                PrintWriter writer = res.getWriter();
                writer.write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Non authentifié\"}");
                writer.flush();
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(publicEndpoints).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthFilter(jwtService, userRepository),
                UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new ApiKeyAuthFilter(apiKeyService), JwtAuthFilter.class)
            .addFilterAfter(rateLimitFilter, JwtAuthFilter.class)
            .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // ── Filtre JWT ─────────────────────────────────────────────────────────
    static class JwtAuthFilter extends OncePerRequestFilter {
        private final JwtService jwt;
        private final UserRepository userRepo;
        JwtAuthFilter(JwtService jwt, UserRepository userRepo) { this.jwt = jwt; this.userRepo = userRepo; }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                         FilterChain chain) throws ServletException, IOException {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                chain.doFilter(req, res); return;
            }
            String h = req.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) {
                String token = h.substring(7);
                if (jwt.isValid(token)) {
                    UUID userId = jwt.extractUserId(token);
                    String role = jwt.extractRole(token);
                    String tokenType = jwt.extractTokenType(token);
                    req.setAttribute("userId", userId);
                    req.setAttribute("plan", jwt.extractPlan(token));
                    req.setAttribute("role", role);
                    req.setAttribute("tokenType", tokenType);
                    if ("CLIENT".equals(tokenType)) {
                        UUID companyId = jwt.extractCompanyId(token);
                        if (companyId != null) {
                            req.setAttribute("companyId", companyId);
                            TenantContext.set(companyId);
                        }
                        setAuth(userId.toString(), "ROLE_CLIENT");
                    } else {
                        String authority = switch (role != null ? role : "USER") {
                            case "OWNER", "ADMIN" -> "ROLE_ADMIN";
                            case "MANAGER" -> "ROLE_MANAGER";
                            case "USER" -> "ROLE_USER";
                            default -> "ROLE_USER";
                        };
                        setAuth(userId.toString(), authority);

                        if (TenantContext.get() == null) {
                            userRepo.findByIdWithCompany(userId).ifPresent(user -> {
                                if (user.getCompany() != null) {
                                    UUID companyId = user.getCompany().getId();
                                    TenantContext.set(companyId);
                                    req.setAttribute("companyId", companyId);
                                }
                            });
                        }
                    }
                }
            }
            chain.doFilter(req, res);
        }
    }

    // ── Filtre API Key ──────────────────────────────────────────────────────
    static class ApiKeyAuthFilter extends OncePerRequestFilter {
        private final ApiKeyService svc;
        ApiKeyAuthFilter(ApiKeyService svc) { this.svc = svc; }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                         FilterChain chain) throws ServletException, IOException {
            String key = req.getHeader("X-API-Key");
            if (key == null) key = req.getParameter("api_key");
            if (key != null && key.startsWith("ic_")) {
                Optional<ApiKeyService.ValidatedKey> v = svc.validate(key);
                if (v.isEmpty()) {
                    writeError(res, 401, "INVALID_API_KEY", "Clé API invalide"); return;
                }
                ApiKeyService.ValidatedKey vk = v.get();
                if (vk.quotaExceeded()) {
                    res.setHeader("X-RateLimit-Limit", String.valueOf(vk.dailyLimit()));
                    res.setHeader("X-RateLimit-Remaining", "0");
                    writeError(res, 429, "QUOTA_EXCEEDED",
                        "Quota journalier dépassé (" + vk.dailyLimit() + "/jour)");
                    return;
                }
                res.setHeader("X-RateLimit-Limit", String.valueOf(vk.dailyLimit()));
                res.setHeader("X-RateLimit-Remaining",
                    String.valueOf(vk.dailyLimit() - vk.callsToday()));
                req.setAttribute("userId", vk.userId());
                req.setAttribute("plan", vk.plan());
                setAuth(vk.userId().toString(), "ROLE_API");
            }
            chain.doFilter(req, res);
        }
    }

    private static void setAuth(String principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority(role)))
        );
    }

    private static void writeError(HttpServletResponse res, int status,
                                    String code, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + msg + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        String origins = System.getenv().getOrDefault("CORS_ORIGINS", "http://localhost:3000,http://localhost:5173,http://localhost:5180");
        cfg.setAllowedOrigins(Arrays.stream(origins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList());
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "X-API-Key", "Content-Type", "Accept", "X-Tenant-Id"));
        cfg.setExposedHeaders(List.of("X-RateLimit-Limit","X-RateLimit-Remaining","Retry-After"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

   
}
