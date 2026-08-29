package com.incokalk.service.ml;

import com.incokalk.config.EmbeddingsConfig;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmbeddingsClient - Test contractuel (format des appels + repli gracieux)")
class EmbeddingsClientContractTest {

    private MockWebServer server;
    private EmbeddingsConfig config;
    private EmbeddingsClient client;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        config = new EmbeddingsConfig();
        config.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
        config.setEnabled(true);
        client = new EmbeddingsClient(new RestTemplate(), config);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    @DisplayName("POST /v1/embeddings/encode avec le batch de textes, un vecteur par texte en retour")
    void encode_contractRequest() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"embeddings\":[[0.1,0.2,0.3],[0.4,0.5,0.6]],\"model\":\"test\",\"dimensions\":3}"));

        List<float[]> result = client.encode(List.of("téléphone intelligent", "smartphone"));

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/v1/embeddings/encode");
        assertThat(recorded.getBody().readUtf8()).contains("téléphone intelligent", "smartphone");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(result.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
    }

    @Test
    @DisplayName("Service indisponible -> liste vide, jamais d'exception propagée")
    void encode_serviceUnavailable_returnsEmptyList() {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<float[]> result = client.encode(List.of("un produit quelconque"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Source désactivée -> aucun appel HTTP n'est fait")
    void encode_disabled_skipsCallEntirely() throws Exception {
        config.setEnabled(false);

        List<float[]> result = client.encode(List.of("un produit quelconque"));

        assertThat(result).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("encodeOne renvoie null quand aucun vecteur n'est produit")
    void encodeOne_noResult_returnsNull() {
        server.enqueue(new MockResponse().setResponseCode(500));

        float[] result = client.encodeOne("un produit quelconque");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("GET /health -> isHealthy() reflète le code de statut")
    void isHealthy_reflectsStatusCode() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"status\":\"ok\"}"));

        assertThat(client.isHealthy()).isTrue();

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/health");
    }
}
