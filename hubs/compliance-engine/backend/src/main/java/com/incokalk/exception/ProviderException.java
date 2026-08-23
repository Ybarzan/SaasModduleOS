package com.incokalk.exception;

import lombok.Getter;

@Getter
public class ProviderException extends RuntimeException {

    private final String providerType;

    public ProviderException(String providerType, String message) {
        super(message);
        this.providerType = providerType;
    }

    public ProviderException(String providerType, String message, Throwable cause) {
        super(message, cause);
        this.providerType = providerType;
    }
}
