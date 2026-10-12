package com.fleethub.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Authentification du portail client (shipper suivant ses propres
 * expéditions) — séparée et parallèle à l'authentification staff
 * ({@link JwtService}/{@link JwtAuthFilter}) : un token client n'est jamais
 * un compte {@code AppUser} et ne doit jamais pouvoir usurper un rôle staff.
 * Utilise volontairement le même secret que le JWT staff (une seule clé à
 * gérer en configuration) mais un type de token distinct et vérifié
 * ({@code type=CLIENT}), et n'alimente jamais {@link TenantContext}/les
 * filtres staff — voir {@link ClientAuthFilter}.
 */
@Service
public class ClientJwtService {

    private final SecretKey key;
    private final long expirationMs;

    public ClientJwtService(@Value("${app.jwt.secret}") String secret,
                             @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID clientId, String email, Long companyId) {
        return Jwts.builder()
                .subject(clientId.toString())
                .claim("email", email)
                .claim("companyId", companyId)
                .claim("type", "CLIENT")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parse(token);
            return "CLIENT".equals(claims.get("type", String.class))
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID extractClientId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public Long extractCompanyId(String token) {
        Object v = parse(token).get("companyId");
        return v == null ? null : Long.valueOf(v.toString());
    }

    public String extractEmail(String token) {
        return parse(token).get("email", String.class);
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
