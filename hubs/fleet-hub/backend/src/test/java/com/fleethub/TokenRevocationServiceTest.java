package com.fleethub;

import com.fleethub.model.RevokedToken;
import com.fleethub.repository.RevokedTokenRepository;
import com.fleethub.repository.UserTokenCutoffRepository;
import com.fleethub.security.JwtService;
import com.fleethub.security.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    private static final String SECRET =
            "fleet-hub-super-secret-key-change-me-in-production-2026-0123456789abcdef";

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    @Mock
    private UserTokenCutoffRepository userTokenCutoffRepository;

    private JwtService jwtService;

    @InjectMocks
    private TokenRevocationService tokenRevocationService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3600000);
        tokenRevocationService = new TokenRevocationService(revokedTokenRepository, userTokenCutoffRepository, jwtService);
    }

    @Test
    void revoke_savesTokenWithCorrectId() {
        String token = jwtService.generateToken("admin", "ADMIN", 42L);
        String tokenId = jwtService.extractTokenId(token);

        when(revokedTokenRepository.existsByTokenId(tokenId)).thenReturn(false);
        when(revokedTokenRepository.save(any(RevokedToken.class))).thenAnswer(inv -> inv.getArgument(0));

        tokenRevocationService.revoke(token);

        verify(revokedTokenRepository).save(argThat(rt ->
                rt.getTokenId().equals(tokenId) &&
                rt.getExpiresAt().isAfter(LocalDateTime.now()) &&
                rt.getRevokedAt() != null
        ));
    }

    @Test
    void revoke_skipsIfAlreadyRevoked() {
        String token = jwtService.generateToken("admin", "ADMIN", 42L);
        String tokenId = jwtService.extractTokenId(token);

        when(revokedTokenRepository.existsByTokenId(tokenId)).thenReturn(true);

        tokenRevocationService.revoke(token);

        verify(revokedTokenRepository, never()).save(any());
    }

    @Test
    void isRevoked_returnsTrueWhenTokenExists() {
        when(revokedTokenRepository.existsByTokenId("some-id")).thenReturn(true);
        assertTrue(tokenRevocationService.isRevoked("some-id"));
    }

    @Test
    void isRevoked_returnsFalseWhenTokenNotExists() {
        when(revokedTokenRepository.existsByTokenId("some-id")).thenReturn(false);
        assertFalse(tokenRevocationService.isRevoked("some-id"));
    }

    @Test
    void isRevoked_returnsFalseForNullId() {
        assertFalse(tokenRevocationService.isRevoked(null));
    }
}
