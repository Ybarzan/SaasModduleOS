package com.logistix.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialEncryptionServiceTest {

    private final CredentialEncryptionService service = new CredentialEncryptionService("test-secret-key");

    @Test
    void encrypt_thenDecrypt_roundTrips() {
        String plainText = "fh-outbound-api-key-123";

        String encrypted = service.encrypt(plainText);

        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(service.decrypt(encrypted)).isEqualTo(plainText);
    }

    @Test
    void encrypt_sameInputTwice_producesDifferentCiphertext() {
        String a = service.encrypt("same-key");
        String b = service.encrypt("same-key");

        assertThat(a).isNotEqualTo(b);
        assertThat(service.decrypt(a)).isEqualTo("same-key");
    }

    @Test
    void encrypt_null_returnsNull() {
        assertThat(service.encrypt(null)).isNull();
    }

    @Test
    void decrypt_null_returnsNull() {
        assertThat(service.decrypt(null)).isNull();
    }

    @Test
    void decrypt_withWrongKey_throws() {
        CredentialEncryptionService otherService = new CredentialEncryptionService("a-different-secret-key");
        String encrypted = service.encrypt("secret");

        assertThrows(IllegalStateException.class, () -> otherService.decrypt(encrypted));
    }
}
