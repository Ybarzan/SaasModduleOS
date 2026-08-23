package com.fleethub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le filtre de limitation (anti brute-force / anti-spam) couvre les deux
 * endpoints publics : {@code /api/auth/login} ET {@code /api/auth/register}.
 * Au-delà de la limite (3 ici), la 4e requête reçoit un 429.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.auth-limit=3",
        "app.security.rate-limit.window-seconds=60",
        "app.security.rate-limit.default-limit=100"
})
class LoginRateLimitTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void register_isRateLimited_afterLimitReached() throws Exception {
        String ip = "10.0.0.1";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/register")
                            .with(r -> {
                                r.setRemoteAddr(ip);
                                return r;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"companyName\":\"RL Reg " + i + "\",\"firstName\":\"A\","
                                    + "\"lastName\":\"B\",\"email\":\"rl-reg-" + i + "@test.fr\","
                                    + "\"password\":\"password123\"}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/auth/register")
                        .with(r -> {
                            r.setRemoteAddr(ip);
                            return r;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"RL Reg 3\",\"firstName\":\"A\","
                                + "\"lastName\":\"B\",\"email\":\"rl-reg-3@test.fr\","
                                + "\"password\":\"password123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void login_isRateLimited_afterLimitReached() throws Exception {
        String ip = "10.0.0.2";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login")
                            .with(r -> {
                                r.setRemoteAddr(ip);
                                return r;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login")
                        .with(r -> {
                            r.setRemoteAddr(ip);
                            return r;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
