package com.fleethub.security;

import com.fleethub.repository.AdminIpAllowlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminIpFilterTest {

    private final AdminIpAllowlistRepository repository = mock(AdminIpAllowlistRepository.class);

    private static MockHttpServletRequest request(String uri, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void nonAdminRoutes_neverQueryTheDatabase() {
        AdminIpFilter filter = new AdminIpFilter("", repository, new ClientIpResolver());

        assertTrue(filter.shouldNotFilter(request("/api/trucks", "1.2.3.4")));

        verify(repository, never()).count();
    }

    @Test
    void adminRoutes_withoutAnyAllowlist_areNotRestricted() {
        when(repository.count()).thenReturn(0L);
        AdminIpFilter filter = new AdminIpFilter("", repository, new ClientIpResolver());

        assertTrue(filter.shouldNotFilter(request("/api/admin/companies", "1.2.3.4")));
    }

    @Test
    void adminRoutes_fromAnIpOutsideTheAllowlist_areRefused() throws Exception {
        when(repository.existsByIpAddress(anyString())).thenReturn(false);
        AdminIpFilter filter = new AdminIpFilter("10.0.0.1", repository, new ClientIpResolver());
        MockHttpServletRequest req = request("/api/admin/companies", "1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertFalse(filter.shouldNotFilter(req));
        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(403, res.getStatus());
    }

    @Test
    void adminRoutes_fromAnAllowedIp_goThrough() throws Exception {
        AdminIpFilter filter = new AdminIpFilter("10.0.0.1", repository, new ClientIpResolver());
        MockHttpServletRequest req = request("/api/admin/companies", "10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals(200, res.getStatus());
    }
}
