package com.praxio.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityStartupGuardTest {

    @Mock
    private Environment environment;

    private SecurityStartupGuard guard;

    @BeforeEach
    void setUp() {
        guard = new SecurityStartupGuard(environment);
        ReflectionTestUtils.setField(guard, "adminPassword", "not-the-default");
        ReflectionTestUtils.setField(guard, "ssoEncryptionKey", "a-distinct-sso-encryption-key");
        ReflectionTestUtils.setField(guard, "corsOrigins", "https://app.example.com");
    }

    @Test
    void nonProdProfile_neverValidates() {
        when(environment.acceptsProfiles("prod")).thenReturn(false);
        ReflectionTestUtils.setField(guard, "adminPassword", "admin");
        assertDoesNotThrow(() -> guard.verify());
    }

    @Test
    void prodProfile_validConfig_doesNotThrow() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        assertDoesNotThrow(() -> guard.verify());
    }

    @Test
    void prodProfile_defaultAdminPassword_throws() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        ReflectionTestUtils.setField(guard, "adminPassword", "admin");
        assertThrows(IllegalStateException.class, () -> guard.verify());
    }

    @Test
    void prodProfile_blankAdminPassword_throws() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        ReflectionTestUtils.setField(guard, "adminPassword", "");
        assertThrows(IllegalStateException.class, () -> guard.verify());
    }

    @Test
    void prodProfile_defaultSsoEncryptionKey_throws() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        ReflectionTestUtils.setField(guard, "ssoEncryptionKey",
                "dev-only-insecure-sso-encryption-key-not-for-production");
        assertThrows(IllegalStateException.class, () -> guard.verify());
    }

    @Test
    void prodProfile_ssoEncryptionKeyEqualsAdminPassword_throws() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        ReflectionTestUtils.setField(guard, "ssoEncryptionKey", "not-the-default");
        assertThrows(IllegalStateException.class, () -> guard.verify());
    }

    @Test
    void prodProfile_blankSsoEncryptionKey_throws() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        ReflectionTestUtils.setField(guard, "ssoEncryptionKey", "");
        assertThrows(IllegalStateException.class, () -> guard.verify());
    }

    @Test
    void prodProfile_wildcardCors_throws() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        ReflectionTestUtils.setField(guard, "corsOrigins", "*");
        assertThrows(IllegalStateException.class, () -> guard.verify());
    }

    @Test
    void prodProfile_blankCors_throws() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        ReflectionTestUtils.setField(guard, "corsOrigins", "");
        assertThrows(IllegalStateException.class, () -> guard.verify());
    }
}
