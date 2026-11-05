package com.fleethub.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Montants : toujours en {@link BigDecimal}, arrondis au centime (demi supérieur)
 * à l'enregistrement. Les KPI analytiques (coût/km, parts…) restent en double :
 * ils sont dérivés de sommes faites ici, en décimal exact.
 */
public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    /** Montant au centime. */
    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Arrondi au centime ; null reste null. */
    public static BigDecimal cents(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** null compté comme zéro (sommes). */
    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Pour un calcul analytique (ratio, moyenne) ; null compté comme zéro. */
    public static double toDouble(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }
}
