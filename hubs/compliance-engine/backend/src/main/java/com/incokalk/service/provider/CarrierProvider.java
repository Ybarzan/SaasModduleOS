package com.incokalk.service.provider;

import com.incokalk.dto.config.ProviderHealthDTO;
import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.exception.ProviderException;

import java.util.List;
import java.util.UUID;

public interface CarrierProvider {

    String getProviderType();

    List<QuoteResponseDTO> getRates(QuoteRequestDTO request, UUID companyId) throws ProviderException;

    boolean isAvailable(UUID companyId);

    ProviderHealthDTO getHealth(UUID companyId);

    default String getName() {
        return getProviderType();
    }

    default String getLogoUrl() {
        return null;
    }
}
