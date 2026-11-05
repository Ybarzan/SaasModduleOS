package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.dto.ShipmentOrderDTO;
import com.fleethub.model.AppUser;
import com.fleethub.model.SharedLink;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.SharedLinkRepository;
import com.fleethub.security.JwtService;
import com.fleethub.security.TokenRevocationService;
import com.fleethub.service.ShipmentService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Durcissement de l'authentification (audit du 04/11) : verrouillage après
 * échecs répétés, PIN chauffeur à 6 chiffres, révocation globale respectée par
 * /refresh et /session-from-token, délai de grâce du refresh, expiration par
 * défaut des liens de suivi publics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthHardeningTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private TokenRevocationService tokenRevocationService;
    @Autowired
    private ShipmentService shipmentService;
    @Autowired
    private SharedLinkRepository sharedLinkRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private String email;
    private String adminToken;
    private String accessCode;

    @BeforeEach
    void setUp() throws Exception {
        email = "hardening-" + System.nanoTime() + "@test.fr";
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Hardening\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        adminToken = body.get("token").asText();
        accessCode = body.get("companyAccessCode").asText();
    }

    private void login(String username, String password, ResultMatcher expected) throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(expected);
    }

    // ---- Verrouillage ----

    @Test
    void fiveFailures_lockTheAccount_evenWithTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-" + i, status().isUnauthorized());
        }
        login(email, PASSWORD, status().isTooManyRequests());
    }

    @Test
    void successfulLogin_resetsTheFailureCounter() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(email, "wrong-" + i, status().isUnauthorized());
        }
        login(email, PASSWORD, status().isOk());
        for (int i = 0; i < 4; i++) {
            login(email, "wrong-" + i, status().isUnauthorized());
        }
        login(email, PASSWORD, status().isOk());
    }

    @Test
    void lockExpires_afterItsDuration() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-" + i, status().isUnauthorized());
        }
        AppUser user = userRepository.findByUsername(email).orElseThrow();
        user.setLockedUntil(LocalDateTime.now().minusSeconds(1));
        userRepository.save(user);
        login(email, PASSWORD, status().isOk());
    }

    // ---- PIN chauffeur ----

    private long createDriver() throws Exception {
        MvcResult res = mvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Pin\",\"licenseNumber\":\"FR-PIN-"
                                + System.nanoTime() + "\",\"phone\":\"01 00 00 00 00\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private void setPin(long driverId, String pin, ResultMatcher expected) throws Exception {
        mvc.perform(post("/api/drivers/" + driverId + "/pin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(expected);
    }

    @Test
    void pin_mustHaveSixDigits_andNotBeTrivial() throws Exception {
        long driverId = createDriver();
        setPin(driverId, "4815", status().isBadRequest());
        setPin(driverId, "000000", status().isBadRequest());
        setPin(driverId, "123456", status().isBadRequest());
        setPin(driverId, "987654", status().isBadRequest());
        setPin(driverId, "481516", status().isNoContent());
    }

    @Test
    void roster_exposesPinLength_legacyAccountsStayAtFour() throws Exception {
        long driverId = createDriver();
        setPin(driverId, "481516", status().isNoContent());
        mvc.perform(get("/api/pointage/roster/" + accessCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drivers[0].pinLength").value(6));

        // Compte créé avant V23 : pin_length NULL => le portail attend 4 chiffres.
        AppUser account = userRepository.findByDriverId(driverId).orElseThrow();
        account.setPinLength(null);
        userRepository.save(account);
        mvc.perform(get("/api/pointage/roster/" + accessCode))
                .andExpect(jsonPath("$.drivers[0].pinLength").value(4));
    }

    @Test
    void lockedChauffeur_isUnlockedWhenAManagerSetsANewPin() throws Exception {
        long driverId = createDriver();
        setPin(driverId, "481516", status().isNoContent());
        String username = "chauffeur-" + driverId + "@pointage.internal";
        for (int i = 0; i < 5; i++) {
            login(username, "00000" + i, status().isUnauthorized());
        }
        login(username, "481516", status().isTooManyRequests());

        setPin(driverId, "271828", status().isNoContent());
        login(username, "271828", status().isOk());
    }

    // ---- Révocation globale et refresh ----

    private Long userId() {
        return userRepository.findByUsername(email).orElseThrow().getId();
    }

    @Test
    void refresh_isRefused_afterRevokeAll() throws Exception {
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("fh_token", adminToken)))
                .andExpect(status().isOk());

        tokenRevocationService.revokeAllForUser(userId());

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("fh_token", adminToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionFromToken_isRefused_afterRevokeAll() throws Exception {
        tokenRevocationService.revokeAllForUser(userId());

        mvc.perform(post("/api/auth/session-from-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + adminToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String tokenExpiredSince(long millis) {
        AppUser user = userRepository.findByUsername(email).orElseThrow();
        return new JwtService(jwtSecret, -millis)
                .generateToken(user.getUsername(), user.getRole(), user.getCompany().getId());
    }

    @Test
    void refresh_acceptsATokenExpiredWithinTheGracePeriod() throws Exception {
        String expired = tokenExpiredSince(60_000);
        MvcResult res = mvc.perform(post("/api/auth/refresh").cookie(new Cookie("fh_token", expired)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(res.getResponse().getCookie("fh_token").getMaxAge() > 0, "un nouveau cookie doit être posé");

        // L'ancien jeton a été révoqué : il ne peut pas servir à un second refresh.
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("fh_token", expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_refusesATokenExpiredBeyondTheGracePeriod() throws Exception {
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("fh_token", tokenExpiredSince(600_000))))
                .andExpect(status().isUnauthorized());
    }

    // ---- Liens de suivi publics ----

    private UUID createShipment() {
        Long companyId = userRepository.findByUsername(email).orElseThrow().getCompany().getId();
        ShipmentOrderDTO dto = ShipmentOrderDTO.builder().shipperName("Labo").consigneeName("Plateau technique").build();
        return shipmentService.createShipment(dto, null, companyId).getId();
    }

    private JsonNode createLink(String body, ResultMatcher expected) throws Exception {
        MvcResult res = mvc.perform(post("/api/shared")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(expected)
                .andReturn();
        String content = res.getResponse().getContentAsString();
        return content.isBlank() ? null : objectMapper.readTree(content);
    }

    @Test
    void sharedLink_withoutExpiry_expiresAfterThirtyDays() throws Exception {
        JsonNode link = createLink("{\"shipmentId\":\"" + createShipment() + "\"}", status().isOk());
        LocalDateTime expiresAt = LocalDateTime.parse(link.get("expiresAt").asText());
        assertTrue(expiresAt.isAfter(LocalDateTime.now().plusDays(29)));
        assertTrue(expiresAt.isBefore(LocalDateTime.now().plusDays(31)));
    }

    @Test
    void sharedLink_expiryMustBePositiveAndAtMostOneYear() throws Exception {
        UUID shipmentId = createShipment();
        createLink("{\"shipmentId\":\"" + shipmentId + "\",\"expiresHours\":0}", status().isBadRequest());
        createLink("{\"shipmentId\":\"" + shipmentId + "\",\"expiresHours\":9000}", status().isBadRequest());
    }

    @Test
    void expiredSharedLink_returns410_notA500() throws Exception {
        JsonNode link = createLink("{\"shipmentId\":\"" + createShipment() + "\",\"expiresHours\":1}", status().isOk());
        SharedLink entity = sharedLinkRepository.findByToken(link.get("token").asText()).orElseThrow();
        entity.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        sharedLinkRepository.save(entity);

        mvc.perform(get("/api/shared/access/" + entity.getToken()))
                .andExpect(status().isGone());
        mvc.perform(get("/api/shared/access/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
