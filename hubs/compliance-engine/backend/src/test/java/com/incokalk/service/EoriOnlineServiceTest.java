package com.incokalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EoriOnlineService — Tests unitaires")
class EoriOnlineServiceTest {

    RestTemplate restTemplate;
    EoriOnlineService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new EoriOnlineService(restTemplate);
        ReflectionTestUtils.setField(service, "baseUrl", "https://ec.europa.eu/taxation_customs/dds2/eos/validation/services");
    }

    private void enableOnlineValidation() {
        ReflectionTestUtils.setField(service, "onlineValidation", true);
    }

    @Test
    @DisplayName("checkEori → validation en ligne désactivée")
    void checkEori_onlineValidationDisabled() {
        // onlineValidation defaults to false (never set)
        var result = service.checkEori("FR123456789");

        assertThat(result.valid()).isFalse();
        assertThat(result.traderName()).isNull();
        assertThat(result.traderAddress()).isNull();
        assertThat(result.message()).isEqualTo("EORI online validation disabled");
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("checkEori → réponse valide avec toutes les infos du titulaire")
    void checkEori_validWithFullTraderDetails() {
        enableOnlineValidation();
        String body = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
              <soapenv:Body>
                <val:validateEORIResponse>
                  <val:valid>true</val:valid>
                  <val:traderName>ACME SARL</val:traderName>
                  <val:traderStreet>12 rue de la Paix</val:traderStreet>
                  <val:traderPostalCode>75002</val:traderPostalCode>
                  <val:traderCity>Paris</val:traderCity>
                  <val:traderCountryCode>FR</val:traderCountryCode>
                </val:validateEORIResponse>
              </soapenv:Body>
            </soapenv:Envelope>
            """;
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        var result = service.checkEori("FR123456789012");

        assertThat(result.valid()).isTrue();
        assertThat(result.traderName()).isEqualTo("ACME SARL");
        assertThat(result.traderAddress()).isEqualTo("12 rue de la Paix, 75002, Paris, FR");
        assertThat(result.message()).isNull();

        verify(restTemplate).postForEntity(
            eq("https://ec.europa.eu/taxation_customs/dds2/eos/validation/services/validation"),
            any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("checkEori → réponse valid=false sans infos titulaire")
    void checkEori_validFalseNoTraderDetails() {
        enableOnlineValidation();
        String body = """
            <soapenv:Envelope>
              <soapenv:Body>
                <val:validateEORIResponse>
                  <val:valid>false</val:valid>
                </val:validateEORIResponse>
              </soapenv:Body>
            </soapenv:Envelope>
            """;
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        var result = service.checkEori("FR000000000000");

        assertThat(result.valid()).isFalse();
        assertThat(result.traderName()).isNull();
        assertThat(result.traderAddress()).isEqualTo("");
        assertThat(result.message()).isNull();
    }

    @Test
    @DisplayName("checkEori → adresse partielle (rue et ville uniquement)")
    void checkEori_partialAddress() {
        enableOnlineValidation();
        String body = """
            <valid>true</valid>
            <traderName>Partial Trader</traderName>
            <traderStreet>Main Street</traderStreet>
            <traderCity>Berlin</traderCity>
            """;
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        var result = service.checkEori("DE123456789");

        assertThat(result.valid()).isTrue();
        assertThat(result.traderName()).isEqualTo("Partial Trader");
        assertThat(result.traderAddress()).isEqualTo("Main Street, Berlin");
    }

    @Test
    @DisplayName("checkEori → statut HTTP non 2xx")
    void checkEori_nonSuccessStatus() {
        enableOnlineValidation();
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("<valid>true</valid>", HttpStatus.INTERNAL_SERVER_ERROR));

        var result = service.checkEori("FR123456789012");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("EORI online check failed");
    }

    @Test
    @DisplayName("checkEori → corps de réponse null")
    void checkEori_nullBody() {
        enableOnlineValidation();
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        var result = service.checkEori("FR123456789012");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("EORI online check failed");
    }

    @Test
    @DisplayName("checkEori → réponse sans champ <valid> (SOAP malformé)")
    void checkEori_missingValidField() {
        enableOnlineValidation();
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("<soapenv:Fault><faultstring>Unexpected error</faultstring></soapenv:Fault>", HttpStatus.OK));

        var result = service.checkEori("FR123456789012");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("EORI online check failed");
    }

    @Test
    @DisplayName("checkEori → réponse sans champ <valid>, corps très court (< 300 caractères)")
    void checkEori_missingValidFieldShortBody() {
        enableOnlineValidation();
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("short", HttpStatus.OK));

        var result = service.checkEori("FR123456789012");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("EORI online check failed");
    }

    @Test
    @DisplayName("checkEori → exception réseau (timeout / erreur de connexion)")
    void checkEori_networkException() {
        enableOnlineValidation();
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RestClientException("Connection timed out"));

        var result = service.checkEori("FR123456789012");

        assertThat(result.valid()).isFalse();
        assertThat(result.traderName()).isNull();
        assertThat(result.traderAddress()).isNull();
        assertThat(result.message()).isEqualTo("EORI online check failed");
    }

    @Test
    @DisplayName("checkEori → exception inattendue générique")
    void checkEori_genericException() {
        enableOnlineValidation();
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("boom"));

        var result = service.checkEori("FR123456789012");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("EORI online check failed");
    }
}
