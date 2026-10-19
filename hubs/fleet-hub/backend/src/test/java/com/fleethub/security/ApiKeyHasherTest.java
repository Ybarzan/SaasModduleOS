package com.fleethub.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    @Test
    void hash_isDeterministic() {
        assertThat(ApiKeyHasher.hash("my-api-key")).isEqualTo(ApiKeyHasher.hash("my-api-key"));
    }

    @Test
    void hash_differentInputs_differentHashes() {
        assertThat(ApiKeyHasher.hash("key-a")).isNotEqualTo(ApiKeyHasher.hash("key-b"));
    }

    @Test
    void hash_isNeverEqualToInput() {
        assertThat(ApiKeyHasher.hash("my-api-key")).isNotEqualTo("my-api-key");
    }

    @Test
    void hash_null_returnsNull() {
        assertThat(ApiKeyHasher.hash(null)).isNull();
    }

    @Test
    void hash_isSixtyFourHexCharacters() {
        String hash = ApiKeyHasher.hash("abc");
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }
}
