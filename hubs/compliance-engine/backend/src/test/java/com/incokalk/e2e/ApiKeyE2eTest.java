package com.incokalk.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clé API de bout en bout, sur un vrai serveur : jusqu'ici, toute clé VALIDE faisait
 * planter la requête (incrementCalls @Modifying hors transaction) et finissait en 401 —
 * aucune intégration machine-à-machine (ex. LogistiX) ne pouvait fonctionner.
 */
class ApiKeyE2eTest extends E2eTestBase {

    private String createKey() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwtToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        var resp = restTemplate.exchange(baseUrl + "/v1/api-keys", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "LogistiX", "plan", "STARTER"), h), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("key");
    }

    private HttpHeaders withKey(String key) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", key);
        return h;
    }

    @Test
    void validKey_reachesRoleAndPlanGatedEndpoint() {
        registerAndSetToken();
        String key = createKey();
        var resp = restTemplate.exchange(baseUrl + "/v1/landed-costs", HttpMethod.GET,
                new HttpEntity<>(withKey(key)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void validKey_targetingAnotherCompany_isRejected() {
        registerAndSetToken();
        String key = createKey();
        HttpHeaders h = withKey(key);
        h.set("X-Tenant-ID", UUID.randomUUID().toString());
        var resp = restTemplate.exchange(baseUrl + "/v1/landed-costs", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).contains("TENANT_MISMATCH");
    }

    @Test
    void unknownKey_isUnauthorized() {
        var resp = restTemplate.exchange(baseUrl + "/v1/landed-costs", HttpMethod.GET,
                new HttpEntity<>(withKey("ic_live_doesnotexist000")), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
