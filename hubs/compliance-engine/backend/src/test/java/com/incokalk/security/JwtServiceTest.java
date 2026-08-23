package com.incokalk.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtService — Tests unitaires")
class JwtServiceTest {

    private JwtService jwtService;
    private final String SECRET = "ThisIsAVeryLongSecretKeyForJwtSigningPurposes1234567890!";
    private final long EXPIRATION_MS = 3600_000L;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        setField("secret", SECRET);
        setField("expirationMs", EXPIRATION_MS);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = JwtService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(jwtService, value);
    }

    // ── generateToken + extraction ───────────────────────────────────────

    @Test
    @DisplayName("Génération token utilisateur et extraction des claims")
    void generateToken_extractAll() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "test@email.com", "PRO", "ADMIN");

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo("test@email.com");
        assertThat(jwtService.extractPlan(token)).isEqualTo("PRO");
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtService.extractTokenType(token)).isEqualTo("USER");
    }

    @Test
    @DisplayName("Token avec role null → rôle par défaut ADMIN")
    void generateToken_nullRole_defaultsToAdmin() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "a@b.com", "FREE", null);

        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
    }

    // ── generateClientToken + extraction ─────────────────────────────────

    @Test
    @DisplayName("Génération token client et extraction companyId")
    void generateClientToken_extractCompanyId() {
        UUID clientId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        String token = jwtService.generateClientToken(clientId, "client@email.com", companyId);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUserId(token)).isEqualTo(clientId);
        assertThat(jwtService.extractEmail(token)).isEqualTo("client@email.com");
        assertThat(jwtService.extractCompanyId(token)).isEqualTo(companyId);
        assertThat(jwtService.extractRole(token)).isEqualTo("CLIENT");
        assertThat(jwtService.extractTokenType(token)).isEqualTo("CLIENT");
    }

    @Test
    @DisplayName("Token utilisateur → extractCompanyId retourne null")
    void userToken_extractCompanyId_returnsNull() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "u@b.com", "FREE", "ADMIN");

        assertThat(jwtService.extractCompanyId(token)).isNull();
    }

    // ── isValid ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Token valide → isValid retourne true")
    void isValid_validToken_true() {
        String token = jwtService.generateToken(UUID.randomUUID(), "a@b.com", "FREE", "ADMIN");

        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("Token de fabrication → isValid retourne false")
    void isValid_forgedToken_false() {
        assertThat(jwtService.isValid("totally.fake.token")).isFalse();
    }

    @Test
    @DisplayName("Token vide → isValid retourne false")
    void isValid_emptyToken_false() {
        assertThat(jwtService.isValid("")).isFalse();
    }

    @Test
    @DisplayName("Token expiré → isValid retourne false")
    void isValid_expiredToken_false() throws Exception {
        setField("expirationMs", -1L);
        String token = jwtService.generateToken(UUID.randomUUID(), "a@b.com", "FREE", "ADMIN");

        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("Token signé avec autre secret → isValid retourne false")
    void isValid_wrongSecret_false() throws Exception {
        JwtService other = new JwtService();
        setFieldOn(other, "secret", "AnotherSecretKeyThatIsAlsoLongEnoughForHmacSha512Algorithm12345!");
        setFieldOn(other, "expirationMs", EXPIRATION_MS);

        String token = other.generateToken(UUID.randomUUID(), "a@b.com", "FREE", "ADMIN");

        assertThat(jwtService.isValid(token)).isFalse();
    }

    private void setFieldOn(JwtService svc, String fieldName, Object value) throws Exception {
        Field field = JwtService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(svc, value);
    }
}
