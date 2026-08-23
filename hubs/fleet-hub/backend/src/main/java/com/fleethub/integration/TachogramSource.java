package com.fleethub.integration;

import com.fleethub.integration.dto.TachographDayDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

@Component
@ConditionalOnProperty(name = "integration.tacho.provider", havingValue = "tachogram")
public class TachogramSource implements TachographSource {

    private final RestClient restClient;

    public TachogramSource(RestClient.Builder builder, IntegrationProperties props) {
        IntegrationProperties.Tacho cfg = props.getTacho();
        this.restClient = builder
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                .build();
    }

    @Override
    public List<TachographDayDto> fetchDrivingDays(LocalDate since) {
        return List.of(restClient.get()
                .uri("/api/v1/driving-days?since={since}", since)
                .retrieve()
                .body(TachographDayDto[].class));
    }
}
