package com.incokalk.model;

/**
 * Type de cumulation préférentielle applicable à un accord commercial EU.
 */
public enum CumulationType {
    /** Aucun cumul autorisé. */
    NONE,
    /** Les matières originaires de l'UE (partie importatrice) sont considérées comme originaires. */
    BILATERAL,
    /** Les matières originaires des pays membres d'un même groupe de cumul sont cumulables (cumul régional / diagonal). */
    DIAGONAL,
    /** Cumul étendu : matières ouvraisonnées dans la zone de cumul incluses sans distinction. */
    FULL
}
