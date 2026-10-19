package com.praxio.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialEncryptionServiceTest {

    private final CredentialEncryptionService service = new CredentialEncryptionService("test-secret-key");

    @Test
    void encrypt_thenDecrypt_roundTrips() {
        String plainText = "super-secret-sso-password";

        String encrypted = service.encrypt(plainText);

        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(service.decrypt(encrypted)).isEqualTo(plainText);
    }

    @Test
    void encrypt_sameInputTwice_producesDifferentCiphertext() {
        // IV aléatoire par appel : deux chiffrements du même mot de passe ne
        // doivent jamais être identiques (sinon une fuite de la base révèlerait
        // que deux comptes partagent le même mot de passe).
        String a = service.encrypt("same-password");
        String b = service.encrypt("same-password");

        assertThat(a).isNotEqualTo(b);
        assertThat(service.decrypt(a)).isEqualTo("same-password");
        assertThat(service.decrypt(b)).isEqualTo("same-password");
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

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> otherService.decrypt(encrypted));
    }
}
