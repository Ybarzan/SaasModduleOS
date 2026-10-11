package com.incokalk.e2e;

import com.incokalk.model.Company;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrats d'API vérifiés sur un vrai serveur (audit 2026-09-29, P0-4/5/6).
 */
class ApiContractE2eTest extends E2eTestBase {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void login() {
        registerAndSetToken();
        upgradeCompanyPlan(Company.Plan.ENTERPRISE);
    }

    /**
     * P0-5 : tout GET dont les paramètres de chemin sont des UUID renvoie 400 (jamais 500) pour
     * un identifiant mal formé. Balayage automatique de toutes les routes, pas une liste figée.
     */
    @Test
    @DisplayName("UUID de chemin invalide → 400 sur chaque endpoint GET concerné")
    void invalidPathUuid_is400_onEveryGetEndpoint() {
        List<String> checked = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod hm = e.getValue();
            if (!info.getMethodsCondition().getMethods().contains(RequestMethod.GET)) continue;
            if (info.getPathPatternsCondition() == null) continue;

            List<MethodParameter> pathParams = Arrays.stream(hm.getMethodParameters())
                .filter(p -> p.hasParameterAnnotation(PathVariable.class)).toList();
            if (pathParams.isEmpty() || !pathParams.stream().allMatch(p -> p.getParameterType() == UUID.class)) continue;
            boolean requiredQueryParams = Arrays.stream(hm.getMethodParameters())
                .map(p -> p.getParameterAnnotation(RequestParam.class))
                .anyMatch(rp -> rp != null && rp.required() && rp.defaultValue().equals(
                    org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE));
            if (requiredQueryParams) continue;

            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                String path = pattern.replaceAll("\\{[^}]+}", "not-a-uuid");
                var resp = restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), String.class);
                checked.add(path);
                if (resp.getStatusCode().value() != 400) {
                    failures.add(path + " -> " + resp.getStatusCode().value());
                }
            }
        }
        assertThat(checked).as("aucune route trouvée : le balayage ne teste rien").hasSizeGreaterThan(10);
        assertThat(failures).as("routes ne renvoyant pas 400 sur UUID invalide").isEmpty();
    }

    @Test
    @DisplayName("UUID invalide en paramètre de requête → 400 (cas relevés par l'audit)")
    void invalidQueryUuid_is400() {
        for (String path : List.of(
                "/v1/shipments/1",
                "/v1/inventory/movements?itemId=1",
                "/v1/finance/early-payment-discount?invoiceId=none")) {
            var resp = getRaw(path);
            assertThat(resp.getStatusCode().value()).as(path).isEqualTo(400);
            assertThat(resp.getBody()).as(path).contains("INVALID_PARAMETER");
        }
    }

    /** P0-4 : une collection vide est sérialisée [] — jamais null, jamais un corps vide. */
    @Test
    @DisplayName("Collections vides → []")
    void emptyCollections_areEmptyArrays() {
        for (String path : List.of(
                "/v1/shipments", "/v1/carriers", "/v1/shipping-rates", "/v1/warehouses",
                "/v1/inventory/items", "/v1/inventory/balances", "/v1/analytics/top-routes",
                "/v1/financials/by-carrier", "/v1/financials/by-lane")) {
            var resp = getRaw(path);
            assertThat(resp.getStatusCode().value()).as(path).isEqualTo(200);
            assertThat(resp.getBody()).as(path).isNotNull();
            assertThat(resp.getBody().trim()).as(path).isEqualTo("[]");
        }
        var lookup = restTemplate.exchange(baseUrl + "/v1/tracking/lookup", HttpMethod.POST,
            new HttpEntity<>(Map.of("trackingNumber", "X1", "mode", "ROAD"), authHeaders()), String.class);
        assertThat(lookup.getBody().trim()).isEqualTo("[]");
    }

    /** P0-6 : un EORI au format valide ne provoque plus de 500. */
    @Test
    @DisplayName("POST /v1/eori/validate sur des EORI au format valide → 200")
    void eoriValidate_validFormat_is200() {
        for (String eori : List.of("FR12345678901234", "DE123456789012")) {
            var resp = post("/v1/eori/validate", Map.of("eori", eori));
            assertThat(resp.getStatusCode().value()).as(eori).isEqualTo(200);
            assertThat(jsonPath(resp, "valid").toString()).as(eori).isEqualTo("true");
            assertThat(jsonPath(resp, "country").toString()).as(eori).isEqualTo(eori.substring(0, 2));
        }
        var bad = post("/v1/eori/validate", Map.of("eori", "XX1"));
        assertThat(bad.getStatusCode().value()).isEqualTo(200);
        assertThat(jsonPath(bad, "valid").toString()).isEqualTo("false");
    }

    /** P0-1/2 + P0-8 : le simulateur n'affiche plus de droit inventé et déclare sa devise. */
    @Test
    @DisplayName("POST /v1/simulate FR→US : droits marqués indisponibles, devise et change déclarés")
    void simulate_nonEuDestination_dutiesUnavailableAndCurrencyDeclared() {
        var body = new LinkedHashMap<String, Object>();
        body.put("incoterm", "DAP");
        body.put("originCountry", "FR");
        body.put("destinationCountry", "US");
        body.put("goodsValue", 10000);
        body.put("currency", "USD");
        body.put("hsCode", "847130");
        body.put("transportMode", "SEA");
        body.put("weightKg", 250);
        var resp = post("/v1/simulate", body);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat((Boolean) jsonPath(resp, "dutiesAvailable")).isFalse();
        assertThat(((Number) jsonPath(resp, "buyerCosts.importDuties")).doubleValue()).isZero();
        assertThat((String) jsonPath(resp, "currency")).isEqualTo("EUR");
        assertThat((String) jsonPath(resp, "inputCurrency")).isEqualTo("USD");
        assertThat(((Number) jsonPath(resp, "fxRate")).doubleValue()).isPositive();
        assertThat((String) jsonPath(resp, "fxSource")).isNotBlank();
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) jsonPath(resp, "warnings");
        assertThat(warnings).anyMatch(w -> w.contains("Droits de douane non inclus"));
    }

    @Test
    @DisplayName("GET /v1/customs/duty FR→US : rateAvailable=false, ni montant ni taux (plus de 180 à 1,8 %)")
    void customsDuty_nonEuDestination_isUnavailable() {
        var resp = get("/v1/customs/duty?hsCode=847130&origin=FR&dest=US&goodsValue=10000");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat((Boolean) jsonPath(resp, "rateAvailable")).isFalse();
        // default-property-inclusion: non_null → les valeurs nulles sont omises du JSON.
        assertThat(resp.getBody()).doesNotContainKeys("dutyAmount", "dutyRate", "mfnRate", "basisType");
        assertThat((String) jsonPath(resp, "dutyType")).isEqualTo("UNAVAILABLE");
        assertThat((String) jsonPath(resp, "currency")).isEqualTo("EUR");
    }
}
