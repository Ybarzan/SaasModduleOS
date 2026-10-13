package com.praxio.service;

import com.praxio.dto.SsoCredentialsRequest;
import com.praxio.dto.SsoCredentialsStatusDto;
import com.praxio.model.SsoCredentials;
import com.praxio.repository.SsoCredentialsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Praxio n'est pas multi-tenant : une seule ligne de configuration
 * ({@link SsoCredentials#SINGLETON_ID}), créée à la première écriture.
 */
@Service
@RequiredArgsConstructor
public class SsoCredentialsService {

    private final SsoCredentialsRepository repository;

    @Transactional(readOnly = true)
    public SsoCredentialsStatusDto getStatus() {
        SsoCredentials creds = repository.findById(SsoCredentials.SINGLETON_ID).orElse(null);
        return creds == null ? new SsoCredentialsStatusDto(false, null, false, null, false, null) : toStatus(creds);
    }

    @Transactional
    public SsoCredentialsStatusDto updateCredentials(SsoCredentialsRequest request) {
        SsoCredentials creds = repository.findById(SsoCredentials.SINGLETON_ID)
                .orElseGet(() -> SsoCredentials.builder().id(SsoCredentials.SINGLETON_ID).build());

        if (request.complianceEngineEmail() != null) {
            creds.setComplianceEngineEmail(blankToNull(request.complianceEngineEmail()));
        }
        if (request.complianceEnginePassword() != null) {
            creds.setComplianceEnginePassword(blankToNull(request.complianceEnginePassword()));
        }
        if (request.fleetHubEmail() != null) {
            creds.setFleetHubEmail(blankToNull(request.fleetHubEmail()));
        }
        if (request.fleetHubPassword() != null) {
            creds.setFleetHubPassword(blankToNull(request.fleetHubPassword()));
        }
        if (request.logistixEmail() != null) {
            creds.setLogistixEmail(blankToNull(request.logistixEmail()));
        }
        if (request.logistixPassword() != null) {
            creds.setLogistixPassword(blankToNull(request.logistixPassword()));
        }
        SsoCredentials saved = repository.save(creds);
        return toStatus(saved);
    }

    /** Lecture interne pour le relais SSO -- jamais exposée telle quelle via l'API. */
    @Transactional(readOnly = true)
    public SsoCredentials getCredentialsOrEmpty() {
        return repository.findById(SsoCredentials.SINGLETON_ID)
                .orElseGet(() -> SsoCredentials.builder().id(SsoCredentials.SINGLETON_ID).build());
    }

    private SsoCredentialsStatusDto toStatus(SsoCredentials creds) {
        return new SsoCredentialsStatusDto(
                isConfigured(creds.getComplianceEngineEmail(), creds.getComplianceEnginePassword()),
                creds.getComplianceEngineEmail(),
                isConfigured(creds.getFleetHubEmail(), creds.getFleetHubPassword()),
                creds.getFleetHubEmail(),
                isConfigured(creds.getLogistixEmail(), creds.getLogistixPassword()),
                creds.getLogistixEmail());
    }

    private boolean isConfigured(String email, String password) {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }

    private String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
