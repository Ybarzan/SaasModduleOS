package com.incokalk.service.integration.finance;

import com.incokalk.model.Company;
import com.incokalk.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Client REST pour hubs/finance (service indépendant, sa propre base --
 * même principe que {@code FleetHubIntegrationClient}). Ne se connecte
 * jamais directement à la base de Finance, toujours via son API HTTP.
 *
 * <p>Remplace, côté compliance-engine, la lecture directe des entités
 * Finance & Trésorerie (ClientInvoice, ShipmentFinancials, CarbonOffset)
 * par AnalyticsService, QualityMetricsService, MultiBranchService,
 * MobileDashboardService et CurrencyController, une fois ces entités
 * retirées de compliance-engine (voir la suppression du domaine Finance).</p>
 *
 * <p><b>Authentification inter-hub :</b> Finance résout son tenant courant à
 * partir d'un JWT émis par lui-même (voir son {@code TenantFilter}) ;
 * compliance-engine n'en a pas. Chaque appel s'authentifie donc via une clé
 * propre à chaque société (en-tête {@code X-Internal-Integration-Key}),
 * générée côté Finance quand celle-ci active le partage ("Intégration
 * interne" dans ses paramètres) puis collée ici (voir
 * {@code Company.financeApiKey} / paramètres entreprise). Finance résout la
 * société depuis cette clé, jamais depuis un identifiant fourni par
 * l'appel -- une société dont la clé n'est pas configurée reçoit une erreur
 * explicite plutôt qu'un appel anonyme qui échouerait en 401 côté Finance.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceIntegrationClient {

    private final RestTemplate restTemplate;
    private final CompanyRepository companyRepository;

    @Value("${incokalk.integration.finance.base-url:http://localhost:8093}")
    private String baseUrl;

    /** Pour AnalyticsService.getDashboardSummary() et le tableau de bord. */
    public record DashboardSummary(long overdueInvoicesCount, BigDecimal overdueInvoicesAmount) {}

    /** Pour QualityMetricsService.invoiceTimeliness(). */
    public record InvoiceTimeliness(long opportunities, long defects) {}

    /** Pour MultiBranchService (consolidation CA/coût/marge). */
    public record FinancialTotals(BigDecimal totalRevenue, BigDecimal totalCost, BigDecimal totalMargin) {}

    /** Pour MultiBranchService et MobileDashboardService (consolidation carbone). */
    public record CarbonTotals(BigDecimal co2EmissionsKg, BigDecimal offsetCreditsRetired, BigDecimal offsetTotalCost) {}

    public DashboardSummary getDashboardSummary(UUID companyId) {
        return get("/v1/internal/dashboard-summary", companyId, DashboardSummary.class);
    }

    public InvoiceTimeliness getInvoiceTimeliness(UUID companyId) {
        return get("/v1/internal/invoice-timeliness", companyId, InvoiceTimeliness.class);
    }

    public FinancialTotals getFinancialTotals(UUID companyId) {
        return get("/v1/internal/financial-totals", companyId, FinancialTotals.class);
    }

    public CarbonTotals getCarbonTotals(UUID companyId) {
        return get("/v1/internal/carbon-totals", companyId, CarbonTotals.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getFxExposureReport(UUID companyId) {
        return get("/v1/internal/fx-exposure-report", companyId, Map.class);
    }

    private <T> T get(String path, UUID companyId, Class<T> responseType) {
        HttpHeaders headers = jsonHeaders();
        headers.set("X-Internal-Integration-Key", resolveApiKey(companyId));

        String url = trimTrailingSlash(baseUrl) + path;
        try {
            ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), responseType);
            T body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new FinanceIntegrationException(
                        "Échec de l'appel Finance (" + path + ") (statut " + response.getStatusCode() + ")");
            }
            return body;
        } catch (FinanceIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new FinanceIntegrationException("Erreur d'appel à Finance (" + path + ") : " + e.getMessage(), e);
        }
    }

    /** Résout la clé d'intégration Finance de cette société -- jamais un
     * identifiant de société transmis tel quel : Finance ne fait confiance
     * qu'à la clé elle-même pour déterminer qui appelle. */
    private String resolveApiKey(UUID companyId) {
        String apiKey = companyId != null
                ? companyRepository.findById(companyId).map(Company::getFinanceApiKey).orElse(null)
                : null;
        if (apiKey == null || apiKey.isBlank()) {
            throw new FinanceIntegrationException(
                    "Intégration Finance non configurée pour cette entreprise (voir Paramètres > Intégrations)");
        }
        return apiKey;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static class FinanceIntegrationException extends RuntimeException {
        public FinanceIntegrationException(String message) {
            super(message);
        }

        public FinanceIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
