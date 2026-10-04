package com.fleethub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Les erreurs de requête du client renvoient un 4xx explicite, jamais un 500. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClientErrorHandlingTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"TEST-Erreurs\",\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"err-"
                                + System.nanoTime() + "@test.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void malformedJson_is400() throws Exception {
        mvc.perform(post("/api/sites").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"x\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void wrongParameterType_is400_andMissingFile_is400() throws Exception {
        String t = token();
        mvc.perform(get("/api/tours?from=pas-une-date").header("Authorization", "Bearer " + t))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders/import?date=2026-10-05").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void wrongMethod_is405() throws Exception {
        mvc.perform(put("/api/orders/dispatch").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
