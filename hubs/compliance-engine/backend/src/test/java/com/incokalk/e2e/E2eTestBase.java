package com.incokalk.e2e;

import com.incokalk.model.Company;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class E2eTestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected CompanyRepository companyRepo;

    @Autowired
    protected UserRepository userRepo;

    protected String baseUrl;
    protected String jwtToken;
    protected UUID currentUserId;

    @BeforeEach
    void setUpBase() {
        baseUrl = "http://localhost:" + port + "/api";
    }

    protected AuthResult registerUser(String email, String password, String fullName, String company) {
        var body = new LinkedHashMap<String, Object>();
        body.put("email", email);
        body.put("password", password);
        body.put("fullName", fullName);
        if (company != null) body.put("company", company);

        var resp = restTemplate.postForEntity(baseUrl + "/v1/auth/register", body, Map.class);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) resp.getBody();
        if (data == null)
            throw new RuntimeException("Registration failed, empty body. Status: " + resp.getStatusCode());
        if (!resp.getStatusCode().is2xxSuccessful())
            throw new RuntimeException("Registration failed. Status: " + resp.getStatusCode() + " body: " + data);

        return new AuthResult(
                (String) data.get("token"), (String) data.get("refreshToken"),
                UUID.fromString((String) data.get("userId")), (String) data.get("email"),
                (String) data.get("plan"), (String) data.get("role"),
                (String) data.get("fullName"));
    }

    protected AuthResult registerAndSetToken() {
        var email = "e2e-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        var r = registerUser(email, "Pass1234!", "Test User", "Test Company");
        this.jwtToken = r.token();
        this.currentUserId = r.userId();
        // L'inscription reelle assigne toujours FREE -- insuffisant pour la quasi-
        // totalite du "happy path" (Shipments, Carriers, Quotes, LandedCost...) qui
        // est desormais gate a STARTER. Meme logique que ControllerTestBase : les
        // tests E2E exercent la logique metier par defaut, pas RequiresPlanAspect ;
        // upgradeCompanyPlan(ENTERPRISE|PRO) reste disponible pour les tests qui
        // visent specifiquement un palier superieur.
        upgradeCompanyPlan(Company.Plan.STARTER);
        return r;
    }

    /** Upgrade test-only du plan de l'entreprise du dernier utilisateur enregistre
     * (registerAndSetToken()) -- l'inscription reelle assigne toujours FREE, ce qui
     * ne suffit pas pour exercer les endpoints geres par RequiresPlanAspect. */
    protected void upgradeCompanyPlan(Company.Plan plan) {
        var user = userRepo.findById(currentUserId).orElseThrow();
        // .getId() seul est sur sur un proxy Hibernate lazy hors session -- tout
        // autre accesseur leverait LazyInitializationException une fois la
        // transaction de findById() refermee. On recharge l'entite via son propre
        // repository plutot que de deferencer le proxy.
        var company = companyRepo.findById(user.getCompany().getId()).orElseThrow();
        company.setPlan(plan);
        companyRepo.save(company);
    }

    protected HttpHeaders authHeaders() {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (jwtToken != null) h.setBearerAuth(jwtToken);
        return h;
    }

    protected ResponseEntity<Map> get(String path) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
    }

    protected ResponseEntity<List> getList(String path) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);
    }

    protected ResponseEntity<String> getRaw(String path) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
    }

    protected <T> ResponseEntity<Map> post(String path, T body) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, authHeaders()), Map.class);
    }

    protected <T> ResponseEntity<Map> put(String path, T body) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.PUT, new HttpEntity<>(body, authHeaders()), Map.class);
    }

    protected ResponseEntity<Map> delete(String path) {
        return restTemplate.exchange(baseUrl + path, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);
    }

    @SuppressWarnings("unchecked")
    protected <T> T jsonPath(ResponseEntity<Map> resp, String key) {
        var body = resp.getBody();
        if (body == null) return null;
        Object cur = body;
        for (String p : key.split("\\.")) {
            if (cur instanceof Map m) cur = m.get(p);
            else return null;
        }
        return (T) cur;
    }

    protected record AuthResult(String token, String refreshToken, UUID userId,
                                String email, String plan, String role, String fullName) {
    }
}
