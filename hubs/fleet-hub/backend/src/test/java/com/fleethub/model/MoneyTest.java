package com.fleethub.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MoneyTest {

    @Test
    void of_roundsToTheCent_halfUp() {
        assertEquals(new BigDecimal("205.01"), Money.of(205.005));
        assertEquals(new BigDecimal("0.30"), Money.of(0.1 + 0.2));
    }

    @Test
    void sums_areExact() {
        BigDecimal total = new BigDecimal("0.10").add(new BigDecimal("0.20"));
        assertEquals(0, total.compareTo(new BigDecimal("0.3")));
        assertEquals(0.30000000000000004, 0.1 + 0.2, "rappel : ce que faisait le stockage en double");
    }

    @Test
    void nullHandling() {
        assertNull(Money.cents(null));
        assertEquals(BigDecimal.ZERO, Money.orZero(null));
        assertEquals(0.0, Money.toDouble(null));
    }
}
