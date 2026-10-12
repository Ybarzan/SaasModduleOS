package com.fleethub.service.provider;

import lombok.Getter;

/** Erreur d'un fournisseur de devis transport (DHL, Shippo, …). */
@Getter
public class CarrierProviderException extends RuntimeException {

    private final String providerType;

    public CarrierProviderException(String providerType, String message) {
        super(message);
        this.providerType = providerType;
    }

    public CarrierProviderException(String providerType, String message, Throwable cause) {
        super(message, cause);
        this.providerType = providerType;
    }
}
