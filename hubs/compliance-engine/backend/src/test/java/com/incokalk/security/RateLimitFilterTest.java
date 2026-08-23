package com.incokalk.security;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RateLimitFilter — Tests unitaires")
class RateLimitFilterTest {

    RateLimitFilter filter;

    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        filter = new RateLimitFilter(Optional.empty());
        ReflectionTestUtils.setField(filter, "freeDaily", 10L);
        ReflectionTestUtils.setField(filter, "proDaily", 500L);
        ReflectionTestUtils.setField(filter, "apiStarterDaily", 2000L);
        ReflectionTestUtils.setField(filter, "apiProDaily", 10000L);
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        when(request.getRequestURI()).thenReturn("/api/v1/carriers");
        when(request.getContextPath()).thenReturn("/api");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    }

    @Test
    @DisplayName("Requêtes anonymes : quota FREE (10/jour), la 11e est bloquée")
    void anonymousRequestsLimitedToFreeQuota() throws Exception {
        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(10)).doFilter(request, response);

        filter.doFilter(request, response, chain);
        verify(response).setStatus(429);
        verify(response).setHeader(eq("Retry-After"), anyString());
        verify(chain, times(10)).doFilter(request, response);
    }

    @Test
    @DisplayName("En-têtes X-RateLimit-Limit / X-RateLimit-Remaining posés")
    void setsRateLimitHeaders() throws Exception {
        when(request.getAttribute("plan")).thenReturn("FREE");
        when(request.getAttribute("userId")).thenReturn(UUID.randomUUID());

        filter.doFilter(request, response, chain);

        verify(response).setHeader("X-RateLimit-Limit", "10");
        verify(response).setHeader("X-RateLimit-Remaining", "9");
        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("Plan PRO : quota 500/jour, la 501e est bloquée")
    void proPlanUsesProQuota() throws Exception {
        when(request.getAttribute("plan")).thenReturn("PRO");
        when(request.getAttribute("userId")).thenReturn(UUID.randomUUID());

        for (int i = 0; i < 500; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(500)).doFilter(request, response);

        filter.doFilter(request, response, chain);
        verify(response).setStatus(429);
    }

    @Test
    @DisplayName("Chaque utilisateur dispose de son propre bucket")
    void separateBucketsPerUser() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        when(request.getAttribute("plan")).thenReturn("FREE");
        when(request.getAttribute("userId")).thenReturn(userA);

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }

        when(request.getAttribute("userId")).thenReturn(userB);
        filter.doFilter(request, response, chain);

        verify(chain, times(11)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("shouldNotFilter exclut login, register et actuator")
    void shouldNotFilterExcludesAuthAndActuator() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/api/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/api/v1/carriers");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Quand un ProxyManager Redis est disponible, les seaux sont résolus via lui (pas la map locale)")
    void usesDistributedProxyManagerWhenPresent() throws Exception {
        ProxyManager<byte[]> proxyManager = org.mockito.Mockito.mock(ProxyManager.class);
        RemoteBucketBuilder<byte[]> builder = org.mockito.Mockito.mock(RemoteBucketBuilder.class);
        io.github.bucket4j.distributed.BucketProxy bucket =
            org.mockito.Mockito.mock(io.github.bucket4j.distributed.BucketProxy.class);
        when(proxyManager.builder()).thenReturn(builder);
        when(builder.build(any(byte[].class), any(Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1))
            .thenReturn(io.github.bucket4j.ConsumptionProbe.consumed(9, 0));

        RateLimitFilter distributedFilter = new RateLimitFilter(Optional.of(proxyManager));
        ReflectionTestUtils.setField(distributedFilter, "freeDaily", 10L);
        when(request.getAttribute("plan")).thenReturn("FREE");
        when(request.getAttribute("userId")).thenReturn(UUID.randomUUID());

        distributedFilter.doFilter(request, response, chain);

        verify(proxyManager).builder();
        verify(builder).build(any(byte[].class), any(Supplier.class));
        verify(chain).doFilter(request, response);
    }
}
