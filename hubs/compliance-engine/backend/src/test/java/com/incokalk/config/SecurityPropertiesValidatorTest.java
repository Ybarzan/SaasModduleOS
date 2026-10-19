package com.incokalk.config;

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
class SecurityPropertiesValidatorTest {

    @Mock
    private Environment environment;

    private SecurityPropertiesValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SecurityPropertiesValidator(environment);
        ReflectionTestUtils.setField(validator, "jwtSecret", "a-strong-enough-secret-that-is-not-a-default-32chars!!");
        ReflectionTestUtils.setField(validator, "apiKeySalt", "a-real-salt");
        ReflectionTestUtils.setField(validator, "mailboxEncryptionKey", "a-distinct-mailbox-key-not-equal-to-jwt-secret");
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
    }

    @Test
    void devProfile_neverValidates() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
        ReflectionTestUtils.setField(validator, "mailboxEncryptionKey", "");
        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void prodProfile_validConfig_doesNotThrow() {
        assertDoesNotThrow(() -> validator.run(null));
    }

    @Test
    void prodProfile_blankMailboxEncryptionKey_throws() {
        ReflectionTestUtils.setField(validator, "mailboxEncryptionKey", "");
        assertThrows(IllegalStateException.class, () -> validator.run(null));
    }

    @Test
    void prodProfile_mailboxEncryptionKeyEqualsJwtSecret_throws() {
        ReflectionTestUtils.setField(validator, "mailboxEncryptionKey",
                ReflectionTestUtils.getField(validator, "jwtSecret"));
        assertThrows(IllegalStateException.class, () -> validator.run(null));
    }

    @Test
    void prodProfile_blankApiKeySalt_throws() {
        ReflectionTestUtils.setField(validator, "apiKeySalt", "");
        assertThrows(IllegalStateException.class, () -> validator.run(null));
    }

    @Test
    void prodProfile_blankJwtSecret_throws() {
        ReflectionTestUtils.setField(validator, "jwtSecret", "");
        assertThrows(IllegalStateException.class, () -> validator.run(null));
    }
}
