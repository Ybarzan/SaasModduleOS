package com.fleethub.security;

import com.fleethub.model.RevokedToken;
import com.fleethub.model.UserTokenCutoff;
import com.fleethub.repository.RevokedTokenRepository;
import com.fleethub.repository.UserTokenCutoffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRevocationService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final UserTokenCutoffRepository userTokenCutoffRepository;
    private final JwtService jwtService;

    /**
     * Révoque un token JWT. Le token reste révoqué jusqu'à son expiration naturelle,
     * après quoi l'entrée est nettoyée automatiquement.
     */
    @Transactional
    public void revoke(String token) {
        try {
            String tokenId = jwtService.extractTokenId(token);
            if (tokenId == null || revokedTokenRepository.existsByTokenId(tokenId)) {
                return;
            }
            var claims = jwtService.extractAllClaims(token);
            var expiresAt = claims.getExpiration().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();

            RevokedToken revoked = new RevokedToken();
            revoked.setTokenId(tokenId);
            revoked.setExpiresAt(expiresAt);
            revoked.setRevokedAt(LocalDateTime.now());
            revokedTokenRepository.save(revoked);
            log.debug("Token révoqué : {}", tokenId);
        } catch (Exception e) {
            log.warn("Impossible de révoquer le token : {}", e.getMessage());
        }
    }

    /**
     * Vérifie si un token (identifié par son jti) a été révoqué.
     */
    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        return revokedTokenRepository.existsByTokenId(tokenId);
    }

    /**
     * Révoque tous les tokens d'un utilisateur en enregistrant un cutoff timestamp.
     * Tout token émis avant cette date sera rejeté par le filtre d'authentification.
     */
    @Transactional
    public void revokeAllForUser(Long userId) {
        UserTokenCutoff cutoff = userTokenCutoffRepository.findByUserId(userId)
                .orElse(new UserTokenCutoff());
        cutoff.setUserId(userId);
        cutoff.setRevokedBefore(LocalDateTime.now());
        userTokenCutoffRepository.save(cutoff);
        log.info("Tous les tokens révoqués pour l'utilisateur {}", userId);
    }

    /**
     * Vérifie si un token a été émis avant le cutoff de révocation globale de l'utilisateur.
     */
    public boolean isRevokedByCutoff(Long userId, Date issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        return userTokenCutoffRepository.findByUserId(userId)
                .map(cutoff -> issuedAt.toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime()
                        .isBefore(cutoff.getRevokedBefore()))
                .orElse(false);
    }

    /**
     * Nettoyage périodique des entrées expirées (token dont la date d'expiration est dépassée).
     * Exécuté toutes les 6 heures.
     */
    @Scheduled(fixedRate = 6 * 3600 * 1000)
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = revokedTokenRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Nettoyage token révoqués : {} entrées expirées supprimées", deleted);
        }
    }
}
