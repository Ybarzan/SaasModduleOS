package com.fleethub.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Client HTTP utilisé pour tester les connexions aux fournisseurs externes
 * (timeouts bornés pour ne pas bloquer la requête HTTP de l'API).
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    /**
     * Les adaptateurs/providers du domaine Transport (carrier booking APIs,
     * fournisseurs de devis DHL/Shippo, tracking AIS/vol/route) ont été portés
     * depuis compliance-engine tels quels et consomment {@code RestTemplate}
     * (pas le {@code RestClient} ci-dessus) — mêmes timeouts bornés.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        return new RestTemplate(factory);
    }

    @Bean
    public RestClientCustomizer integrationRestClientCustomizer() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        return builder -> builder.requestFactory(factory);
    }
}
