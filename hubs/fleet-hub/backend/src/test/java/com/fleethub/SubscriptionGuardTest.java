package com.fleethub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le garde d'abonnement gèle les données métier d'un tenant dont l'essai a
 * expiré (402) tout en laissant la facturation et les droits RGPD accessibles.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionGuardTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CompanyRepository companyRepository;

    private String token;
    private Long companyId;

    @BeforeEach
    void registerTenant() throws Exception {
        String email = "guard-" + UUID.randomUUID().toString().substring(0, 8) + "@test.fr";
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Guard Co\",\"firstName\":\"Alice\","
                                + "\"lastName\":\"Guard\",\"email\":\"" + email
                                + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> res = objectMapper.readValue(body, Map.class);
        token = res.get("token").toString();
        companyId = ((Number) res.get("companyId")).longValue();
    }

    @Test
    void activeTrial_accessesDataAndBilling() throws Exception {
        mvc.perform(get("/api/trucks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/billing/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL"));
    }

    @Test
    void expiredTrial_dataFrozenButBillingAllowed() throws Exception {
        expireTrial();
        mvc.perform(get("/api/trucks").header("Authorization", "Bearer " + token))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/api/billing/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL"));
    }

    private void expireTrial() {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setTrialEndsAt(LocalDateTime.now().minusDays(1));
        companyRepository.save(company);
    }
}
