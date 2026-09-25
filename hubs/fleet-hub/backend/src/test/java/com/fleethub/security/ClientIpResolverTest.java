package com.fleethub.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Vérifie que la résolution de l'IP cliente ne se fie jamais à un
 * {@code X-Forwarded-For} entièrement forgé par le client (voir le
 * javadoc de {@link ClientIpResolver} pour la topologie de référence :
 * client -> Caddy -> nginx -> backend, 2 hops de confiance).
 */
class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver();
        ReflectionTestUtils.setField(resolver, "trustedProxyHops", 2);
    }

    @Test
    void noForwardedHeader_fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        assertEquals("203.0.113.9", resolver.resolve(request));
    }

    @Test
    void twoHops_asAddedByCaddyThenNginx_resolvesRealClientIp() {
        // Caddy ajoute l'IP réelle du client (198.51.100.7), nginx ajoute ensuite
        // sa propre vue de son pair direct (le conteneur Caddy).
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.7, 172.18.0.3");
        assertEquals("198.51.100.7", resolver.resolve(request));
    }

    @Test
    void spoofedPrefix_isIgnored_regardlessOfLength() {
        // Un attaquant peut préfixer n'importe quoi : seules les 2 dernières
        // valeurs (écrites par Caddy puis nginx) doivent compter.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For",
                "9.9.9.9, 8.8.8.8, 7.7.7.7, 198.51.100.7, 172.18.0.3");
        assertEquals("198.51.100.7", resolver.resolve(request));
    }

    @Test
    void headerShorterThanTrustedHops_fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.3");
        request.addHeader("X-Forwarded-For", "198.51.100.7");
        assertEquals("172.18.0.3", resolver.resolve(request));
    }
}
