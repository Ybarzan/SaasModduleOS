package com.incokalk.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    private final LoginRateLimiter limiter = new LoginRateLimiter();

    @Test
    void isBlocked_underLimit_neverBlocks() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.isBlocked("ops@acme.fr")).isFalse();
        }
    }

    @Test
    void isBlocked_overLimit_blocksUntilReset() {
        for (int i = 0; i < 5; i++) {
            limiter.isBlocked("ops@acme.fr");
        }
        assertThat(limiter.isBlocked("ops@acme.fr")).isTrue();

        limiter.reset("ops@acme.fr");

        assertThat(limiter.isBlocked("ops@acme.fr")).isFalse();
    }

    @Test
    void isBlocked_caseInsensitive_sharesTheSameBucket() {
        for (int i = 0; i < 5; i++) {
            limiter.isBlocked("Ops@Acme.fr");
        }
        assertThat(limiter.isBlocked("ops@acme.fr")).isTrue();
    }

    @Test
    void evictStaleEntries_removesBucketsUntouchedForOverThirtyMinutes() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        ReflectionTestUtils.setField(limiter, "clock", clock);

        limiter.isBlocked("stale@acme.fr");
        assertThat(ReflectionTestUtils.getField(limiter, "loginBuckets"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .hasSize(1);

        clock.advance(Duration.ofMinutes(31));
        ReflectionTestUtils.invokeMethod(limiter, "evictStaleEntries");

        assertThat(ReflectionTestUtils.getField(limiter, "loginBuckets"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .isEmpty();
    }

    @Test
    void evictStaleEntries_keepsRecentlyAccessedBuckets() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        ReflectionTestUtils.setField(limiter, "clock", clock);

        limiter.isBlocked("recent@acme.fr");
        clock.advance(Duration.ofMinutes(10));
        ReflectionTestUtils.invokeMethod(limiter, "evictStaleEntries");

        assertThat(ReflectionTestUtils.getField(limiter, "loginBuckets"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .hasSize(1);
    }

    /** Horloge mutable minimale pour avancer le temps déterministiquement dans ces tests. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
