package com.fleethub.security;

import com.fleethub.model.AppUser;
import com.fleethub.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Verrouillage temporaire d'un compte après des échecs de connexion répétés.
 * <p>
 * Complète {@link LoginRateLimitFilter}, qui limite par adresse IP : un attaquant
 * qui répartit ses essais sur plusieurs IP restait sinon libre d'essayer les
 * 10 000 PIN d'un compte CHAUFFEUR, dont l'identifiant est prévisible. Ici le
 * compteur est porté par le compte lui-même, quelle que soit l'origine des essais.
 * <p>
 * Les écritures se font en {@code REQUIRES_NEW} : la connexion appelante échoue
 * en levant une exception, ce qui annulerait sinon l'incrément du compteur.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private final AppUserRepository userRepository;

    @Value("${app.security.lockout.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.lockout.duration-minutes:15}")
    private int lockMinutes;

    /** Compte un échec ; verrouille le compte au {@code maxAttempts}-ième échec consécutif. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFailure(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= maxAttempts) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
                user.setFailedLoginAttempts(0);
                log.warn("Compte {} verrouillé {} min après {} échecs de connexion",
                        user.getId(), lockMinutes, attempts);
            } else {
                user.setFailedLoginAttempts(attempts);
            }
            userRepository.save(user);
        });
    }

    /** Remet le compteur à zéro après une connexion réussie. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSuccess(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        });
    }

    public static boolean isLocked(AppUser user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    public int getLockMinutes() {
        return lockMinutes;
    }
}
