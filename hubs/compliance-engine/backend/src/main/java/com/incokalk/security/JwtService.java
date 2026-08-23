package com.incokalk.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    @Value("${incokalk.security.jwt.secret}")
    private String secret;

    @Value("${incokalk.security.jwt.expiration}")
    private long expirationMs;

    public String generateToken(UUID userId, String email, String plan, String role) {
        return Jwts.builder()
            .subject(userId.toString())
            .claims(Map.of("email", email, "plan", plan, "role", role != null ? role : "ADMIN", "type", "USER"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getKey())
            .compact();
    }

    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
            .subject(userId.toString())
            .claims(Map.of("type", "REFRESH"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .signWith(getKey())
            .compact();
    }

    public String generateClientToken(UUID clientId, String email, UUID companyId) {
        return Jwts.builder()
            .subject(clientId.toString())
            .claims(Map.of(
                "email", email,
                "companyId", companyId.toString(),
                "role", "CLIENT",
                "type", "CLIENT"
            ))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getKey())
            .compact();
    }

    public boolean isValid(String token) {
        try { getClaims(token); return true; }
        catch (Exception e) { return false; }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(getClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    public String extractPlan(String token) {
        return getClaims(token).get("plan", String.class);
    }

    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public String extractTokenType(String token) {
        return getClaims(token).get("type", String.class);
    }

    public UUID extractCompanyId(String token) {
        String cid = getClaims(token).get("companyId", String.class);
        return cid != null ? UUID.fromString(cid) : null;
    }

    private Claims getClaims(String token) {
        return Jwts.parser().verifyWith(getKey()).build()
            .parseSignedClaims(token).getPayload();
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
