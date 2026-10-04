package com.fleethub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les endpoints GET anonymes (permitAll côté SecurityConfig, protégés par un code/clé
 * imprévisible plutôt que par JWT) sont désormais soumis au rate limiting par défaut,
 * comme n'importe quelle autre route non whitelistée — voir
 * LoginRateLimitFilter.PUBLIC_GET_PREFIXES.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.default-limit=3",
        "app.security.rate-limit.window-seconds=60",
        "app.security.login.rate-limit=100"
})
class PublicGetRateLimitTest {

    @Autowired
    private MockMvc mvc;

    private MockHttpServletRequestBuilder from(String uri, String ip) {
        return get(uri).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    @Test
    void roster_isRateLimited_afterDefaultLimitReached() throws Exception {
        String ip = "10.0.1.1";
        for (int i = 0; i < 3; i++) {
            mvc.perform(from("/api/pointage/roster/DOES-NOT-EXIST", ip))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(from("/api/pointage/roster/DOES-NOT-EXIST", ip))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void marketplaceAvailability_isRateLimited_afterDefaultLimitReached() throws Exception {
        String ip = "10.0.1.2";
        for (int i = 0; i < 3; i++) {
            mvc.perform(from("/api/marketplace/availability", ip))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(from("/api/marketplace/availability", ip))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void otherGetRoutes_remainUnaffected_byDefaultLimit() throws Exception {
        // Garde-fou : les GET authentifiés (dashboard, etc.) ne passent toujours pas
        // par ce filtre (401 avant même d'atteindre le rate limiter), contrairement
        // aux routes publiques ci-dessus.
        String ip = "10.0.1.3";
        for (int i = 0; i < 5; i++) {
            mvc.perform(from("/api/dashboard/summary", ip))
                    .andExpect(status().isUnauthorized());
        }
    }
}
