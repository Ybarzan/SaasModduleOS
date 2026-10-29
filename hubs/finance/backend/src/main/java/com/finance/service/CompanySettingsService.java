package com.finance.service;

import com.finance.exception.ResourceNotFoundException;
import com.finance.model.Company;
import com.finance.repository.CompanyRepository;
import com.finance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanySettingsService {

    private final CompanyRepository companyRepo;

    /** true si une clé d'intégration fleet-hub est enregistrée pour cette société
     * -- jamais la clé elle-même (voir FleetHubIntegrationClient). */
    public boolean hasFleetHubApiKey() {
        return companyRepo.findById(TenantContext.get())
                .map(Company::getFleetHubApiKey)
                .filter(k -> !k.isBlank())
                .isPresent();
    }

    @Transactional
    public void setFleetHubApiKey(String apiKey) {
        Company company = companyRepo.findById(TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Société non trouvée"));
        company.setFleetHubApiKey(apiKey != null && !apiKey.isBlank() ? apiKey.trim() : null);
        companyRepo.save(company);
    }
}
