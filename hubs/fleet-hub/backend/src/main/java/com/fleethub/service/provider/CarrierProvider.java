package com.fleethub.service.provider;

import com.fleethub.dto.ProviderHealthDTO;
import com.fleethub.dto.QuoteRequestDTO;
import com.fleethub.dto.QuoteResponseDTO;

import java.util.List;

public interface CarrierProvider {

    String getProviderType();

    List<QuoteResponseDTO> getRates(QuoteRequestDTO request, Long companyId) throws CarrierProviderException;

    boolean isAvailable(Long companyId);

    ProviderHealthDTO getHealth(Long companyId);

    default String getName() {
        return getProviderType();
    }

    default String getLogoUrl() {
        return null;
    }
}
