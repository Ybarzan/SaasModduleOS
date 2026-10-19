package com.finance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S'enregistre via le vrai /v1/auth/register plutôt que de forger un JWT à la
 * main : JwtAuthFilter résout l'utilisateur en base (UserDetailsServiceImpl),
 * donc un jeton signé pour un email qui n'existe pas en base échoue en 401.
 * Le compte créé est toujours ADMIN (rôle du propriétaire à l'inscription).
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class ControllerTestBase {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    protected String jwtToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "controller-test-" + UUID.randomUUID() + "@example.com";
        String body = "{\"companyName\":\"Test Co\",\"displayName\":\"Test Admin\","
                + "\"email\":\"" + email + "\",\"password\":\"password123\"}";
        String response = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        jwtToken = objectMapper.readValue(response, Map.class).get("token").toString();
    }

    protected String authHeader() {
        return "Bearer " + jwtToken;
    }
}
