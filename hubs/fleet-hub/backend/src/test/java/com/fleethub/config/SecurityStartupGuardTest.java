package com.fleethub.config;

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
        ReflectionTestUtils.setField(guard, "jwtSecret", "a-strong-enough-secret-that-is-not-a-default-32chars!!");
        ReflectionTestUtils.setField(guard, "adminPassword", "not-the-default");
        ReflectionTestUtils.setField(guard, "gestPassword", "not-the-default-either");
        ReflectionTestUtils.setField(guard, "corsOrigins", "https://app.example.com");
        ReflectionTestUtils.setField(guard, "integrationSecretKey", "a-real-integration-secret-not-the-dev-default");
    }

    @Test
    void nonProdProfile_neverValidates() {
        when(environment.acceptsProfiles("prod")).thenReturn(false);
        ReflectionTestUtils.setField(guard, "jwtSecret", "");
        assertDoesNotThrow(() -> guard.verify());
    }

    @Test
    void prodProfile_validConfig_doesNotThrow() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        assertDoesNotThrow(() -> guard.verify());
    }

    @Test
    void prodProfile_defaultJwtSecret_throws() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        ReflectionTestUtils.setField(guard, "jwtSecret", "dev-only-change-me-in-prod-2026!secret-key-32chars!!");
        assertThrows(IllegalStateException.class, () -> guard.verify());
    }

    @Test
    void prodProfile_wildcardCors_throwsInsteadOfWarning() {
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

    @Test
    void prodProfile_defaultIntegrationSecret_throws() {
        when(environment.acceptsProfiles("prod")).thenReturn(true);
        ReflectionTestUtils.setField(guard, "integrationSecretKey", "dev-only-integration-secret-change-me");
        assertThrows(IllegalStateException.class, () -> guard.verify());
    }
}
