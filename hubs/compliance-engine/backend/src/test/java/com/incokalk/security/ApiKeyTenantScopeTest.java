package com.incokalk.security;

import com.incokalk.service.ApiKeyService;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Une clé API est créée pour une société précise : le tenant doit venir de la clé,
 * pas d'un en-tête X-Tenant-ID choisi par l'appelant (sinon la clé agit hors de
 * son périmètre sur toute autre société dont son propriétaire est membre).
 */
class ApiKeyTenantScopeTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID KEY_COMPANY = UUID.randomUUID();
    private static final UUID OTHER_COMPANY = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private SecurityConfig.ApiKeyAuthFilter filterFor(UUID keyCompany) {
        ApiKeyService svc = mock(ApiKeyService.class);
        when(svc.validate(anyString())).thenReturn(Optional.of(
                new ApiKeyService.ValidatedKey(UUID.randomUUID(), USER, "ENTERPRISE", Integer.MAX_VALUE, 1, false, keyCompany)));
        return new SecurityConfig.ApiKeyAuthFilter(svc);
    }

    private MockHttpServletRequest request(String tenantHeader) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/hs-suggestions/suggest");
        req.addHeader("X-API-Key", "ic_live_abcdefgh123");
        if (tenantHeader != null) req.addHeader("X-Tenant-ID", tenantHeader);
        return req;
    }

    @Test
    void tenantComesFromTheKey_whenNoHeaderIsSent() throws Exception {
        AtomicReference<UUID> seen = new AtomicReference<>();
        MockHttpServletRequest req = request(null);
        filterFor(KEY_COMPANY).doFilter(req, new MockHttpServletResponse(),
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest r, jakarta.servlet.ServletResponse s) {
                        seen.set(TenantContext.get());
                    }
                });
        assertThat(seen.get()).isEqualTo(KEY_COMPANY);
        assertThat(req.getAttribute("companyId")).isEqualTo(KEY_COMPANY);
    }

    @Test
    void headerTargetingAnotherCompany_isRejected() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filterFor(KEY_COMPANY).doFilter(request(OTHER_COMPANY.toString()), res, chain);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("TENANT_MISMATCH");
        assertThat(chain.getRequest()).as("la requête ne doit pas continuer").isNull();
    }

    @Test
    void headerMatchingTheKeyCompany_isAccepted() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filterFor(KEY_COMPANY).doFilter(request(KEY_COMPANY.toString()), res, chain);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void legacyKeyWithoutCompany_keepsHeaderBasedBehaviour() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filterFor(null).doFilter(request(OTHER_COMPANY.toString()), res, chain);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }
}
