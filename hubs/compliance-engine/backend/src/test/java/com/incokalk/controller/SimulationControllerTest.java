package com.incokalk.controller;

import com.incokalk.service.SimulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SimulationControllerTest extends ControllerTestBase {

    @MockBean
    private SimulationService simulationService;

    @Test
    @DisplayName("POST /v1/simulate → 200 avec un corps valide (endpoint public)")
    void simulateValidBody() throws Exception {
        mockMvc.perform(post("/v1/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incoterm":"FOB","originCountry":"CN","destinationCountry":"FR",\
                                "goodsValue":10000,"currency":"EUR","transportMode":"SEA",\
                                "insuranceLevel":"STANDARD","compareWithOthers":false}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /v1/simulate → 400 VALIDATION_ERROR si goodsValue est absent")
    void simulateMissingGoodsValue() throws Exception {
        mockMvc.perform(post("/v1/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incoterm":"FOB","originCountry":"CN","destinationCountry":"FR","currency":"EUR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.goodsValue").value("must not be null"));
    }

    @Test
    @DisplayName("POST /v1/simulate → 400 si l'Incoterm est inconnu")
    void simulateUnknownIncoterm() throws Exception {
        mockMvc.perform(post("/v1/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incoterm":"ZZZ","originCountry":"CN","destinationCountry":"FR",\
                                "goodsValue":10000,"currency":"EUR"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /v1/simulate → 400 VALIDATION_ERROR si le pays d'origine manque")
    void simulateMissingOrigin() throws Exception {
        mockMvc.perform(post("/v1/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"incoterm":"FOB","destinationCountry":"FR","goodsValue":10000,"currency":"EUR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
