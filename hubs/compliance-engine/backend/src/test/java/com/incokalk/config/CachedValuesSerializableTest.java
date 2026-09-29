package com.incokalk.config;

import com.incokalk.service.CurrencyExchangeService;
import com.incokalk.service.CurrencyService;
import com.incokalk.service.CustomsDutyService;
import com.incokalk.service.EoriOnlineService;
import com.incokalk.service.ViesClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Les valeurs mises en cache Redis sont sérialisées en JDK : une valeur non Serializable fait
 * échouer la requête (cas réel : POST /v1/eori/validate → 500 sur tout EORI au format valide).
 */
class CachedValuesSerializableTest {

    private static void serialize(Object o) throws Exception {
        try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(o);
        }
    }

    @Test
    @DisplayName("EoriCheck, ViesCheck et DutyResult sont sérialisables")
    void cachedRecordsAreSerializable() {
        assertThatCode(() -> serialize(new EoriOnlineService.EoriCheck(true, "ACME", "1 rue X", null)))
            .doesNotThrowAnyException();
        assertThatCode(() -> serialize(new ViesClient.ViesCheck(true, "ACME", "1 rue X", null)))
            .doesNotThrowAnyException();
        assertThatCode(() -> serialize(CustomsDutyService.DutyResult.unavailable("test")))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Une erreur de cache est traitée comme un défaut de cache, pas propagée")
    void cacheErrorsAreSwallowed() {
        var handler = new CacheConfig().errorHandler();
        var cache = new org.springframework.cache.concurrent.ConcurrentMapCache("eori-check");
        assertThatCode(() -> {
            handler.handleCacheGetError(new RuntimeException("Cannot serialize"), cache, "k");
            handler.handleCachePutError(new RuntimeException("Cannot serialize"), cache, "k", "v");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CurrencyService.quote : taux et provenance déclarés")
    void currencyQuoteDeclaresSource() {
        CurrencyService svc = new CurrencyService(mock(CurrencyExchangeService.class));
        assertThat(svc.quote("EUR").source()).isEqualTo("IDENTITY");
        var usd = svc.quote("usd");
        assertThat(usd.currency()).isEqualTo("USD");
        assertThat(usd.source()).isEqualTo("STATIC_FALLBACK");
        assertThat(svc.toEur(1000, "USD")).isEqualTo(Math.round(1000 * usd.rateToEur() * 100.0) / 100.0);
    }
}
