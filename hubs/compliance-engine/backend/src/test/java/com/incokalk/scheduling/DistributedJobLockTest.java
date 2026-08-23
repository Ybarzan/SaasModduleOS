package com.incokalk.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DistributedJobLock — Tests unitaires")
class DistributedJobLockTest {

    @Test
    @DisplayName("Sans Redis configuré, le job s'exécute toujours (no-op transparent)")
    void runsAlwaysWhenRedisAbsent() {
        DistributedJobLock lock = new DistributedJobLock(Optional.empty());
        AtomicBoolean ran = new AtomicBoolean(false);

        lock.runExclusively("job", Duration.ofMinutes(1), () -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("Avec Redis, le verrou acquis exécute le job puis le libère")
    void runsAndReleasesLockWhenAcquired() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("job-lock:job"), any(), any(Duration.class))).thenReturn(true);

        DistributedJobLock lock = new DistributedJobLock(Optional.of(redis));
        AtomicBoolean ran = new AtomicBoolean(false);

        lock.runExclusively("job", Duration.ofMinutes(1), () -> ran.set(true));

        assertThat(ran).isTrue();
        verify(redis).delete("job-lock:job");
    }

    @Test
    @DisplayName("Avec Redis, le verrou refusé (déjà pris) n'exécute pas le job")
    void skipsJobWhenLockDenied() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("job-lock:job"), any(), any(Duration.class))).thenReturn(false);

        DistributedJobLock lock = new DistributedJobLock(Optional.of(redis));
        AtomicBoolean ran = new AtomicBoolean(false);

        lock.runExclusively("job", Duration.ofMinutes(1), () -> ran.set(true));

        assertThat(ran).isFalse();
        verify(redis, times(0)).delete("job-lock:job");
    }

    @Test
    @DisplayName("Le verrou est libéré même si le job lève une exception")
    void releasesLockEvenWhenJobThrows() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("job-lock:job"), any(), any(Duration.class))).thenReturn(true);

        DistributedJobLock lock = new DistributedJobLock(Optional.of(redis));

        try {
            lock.runExclusively("job", Duration.ofMinutes(1), () -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException ignored) {
            // attendu
        }

        verify(redis).delete("job-lock:job");
    }
}
