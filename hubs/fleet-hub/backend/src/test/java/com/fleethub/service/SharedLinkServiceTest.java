package com.fleethub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Durée de vie des liens de suivi publics : jamais illimitée. */
class SharedLinkServiceTest {

    @Test
    void sansDureeDemandee_leLienExpireApres30Jours() {
        assertEquals(30 * 24, SharedLinkService.effectiveExpiresHours(null));
    }

    @Test
    void dureeDemandee_estRespecteeDansLesBornes() {
        assertEquals(48, SharedLinkService.effectiveExpiresHours(48));
        assertEquals(365 * 24, SharedLinkService.effectiveExpiresHours(365 * 24));
    }

    @Test
    void dureeHorsBornes_estRefusee() {
        assertThrows(IllegalArgumentException.class, () -> SharedLinkService.effectiveExpiresHours(0));
        assertThrows(IllegalArgumentException.class, () -> SharedLinkService.effectiveExpiresHours(-5));
        assertThrows(IllegalArgumentException.class, () -> SharedLinkService.effectiveExpiresHours(365 * 24 + 1));
    }
}
