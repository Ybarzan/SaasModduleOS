package com.fleethub;

import com.fleethub.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "fleet-hub-super-secret-key-change-me-in-production-2026-0123456789abcdef";

    private final JwtService jwtService = new JwtService(SECRET, 3600000);

    @Test
    void generateToken_thenExtractUsername_roundTrip() {
        String token = jwtService.generateToken("admin", "ADMIN", 42L);
        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("admin", jwtService.extractUsername(token));
        assertEquals(42L, jwtService.extractCompanyId(token));
    }

    @Test
    void generateToken_withoutCompany_roundTrip() {
        String token = jwtService.generateToken("saasadmin", "SAAS_ADMIN", null);
        assertNull(jwtService.extractCompanyId(token));
    }

    @Test
    void isTokenValid_acceptsCorrectUser() {
        UserDetails user = User.withUsername("admin").password("x").roles("ADMIN").build();
        String token = jwtService.generateToken("admin", "ADMIN", 42L);
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void isTokenValid_rejectsWrongUser() {
        UserDetails user = User.withUsername("other").password("x").roles("ADMIN").build();
        String token = jwtService.generateToken("admin", "ADMIN", 42L);
        assertFalse(jwtService.isTokenValid(token, user));
    }

    @Test
    void isTokenValid_rejectsExpiredToken() {
        JwtService expired = new JwtService(SECRET, -1000);
        UserDetails user = User.withUsername("admin").password("x").roles("ADMIN").build();
        String token = expired.generateToken("admin", "ADMIN", 42L);
        assertFalse(expired.isTokenValid(token, user));
    }

    @Test
    void extractUsername_rejectsInvalidToken() {
        assertThrows(Exception.class, () -> jwtService.extractUsername("not.a.valid.token"));
    }

    @Test
    void generateToken_includesTokenId() {
        String token = jwtService.generateToken("admin", "ADMIN", 42L);
        String tokenId = jwtService.extractTokenId(token);
        assertNotNull(tokenId);
        assertFalse(tokenId.isBlank());
        // Le jti est un UUID valide
        assertDoesNotThrow(() -> java.util.UUID.fromString(tokenId));
    }

    @Test
    void extractAllClaims_returnsClaims() {
        String token = jwtService.generateToken("admin", "ADMIN", 42L);
        io.jsonwebtoken.Claims claims = jwtService.extractAllClaims(token);
        assertEquals("admin", claims.getSubject());
        assertNotNull(claims.getId());
    }
}
