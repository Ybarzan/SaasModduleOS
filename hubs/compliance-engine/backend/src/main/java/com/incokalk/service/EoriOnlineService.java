package com.incokalk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class EoriOnlineService {

    private static final String SOAP_BODY = """
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:val="http://ec.europa.eu/taxation_customs/eos/validation/ws">
          <soapenv:Header/>
          <soapenv:Body>
            <val:validateEORI>
              <val:eoriNumber>%s</val:eoriNumber>
            </val:validateEORI>
          </soapenv:Body>
        </soapenv:Envelope>
        """;

    private static final Pattern VALID_PATTERN = Pattern.compile("<(?:\\w+:)?valid>(true|false)</(?:\\w+:)?valid>");
    private static final Pattern TRADER_NAME_PATTERN = Pattern.compile("<(?:\\w+:)?traderName>(.*?)</(?:\\w+:)?traderName>");
    private static final Pattern TRADER_STREET_PATTERN = Pattern.compile("<(?:\\w+:)?traderStreet>(.*?)</(?:\\w+:)?traderStreet>");
    private static final Pattern TRADER_CITY_PATTERN = Pattern.compile("<(?:\\w+:)?traderCity>(.*?)</(?:\\w+:)?traderCity>");
    private static final Pattern TRADER_POSTAL_PATTERN = Pattern.compile("<(?:\\w+:)?traderPostalCode>(.*?)</(?:\\w+:)?traderPostalCode>");
    private static final Pattern TRADER_COUNTRY_PATTERN = Pattern.compile("<(?:\\w+:)?traderCountryCode>(.*?)</(?:\\w+:)?traderCountryCode>");

    private final RestTemplate restTemplate;

    @Value("${incokalk.eori.base-url:https://ec.europa.eu/taxation_customs/dds2/eos/validation/services}")
    private String baseUrl;

    @Value("${incokalk.eori.online-validation:false}")
    private boolean onlineValidation;

    public record EoriCheck(boolean valid, String traderName, String traderAddress, String message) {}

    @Cacheable(value = "eori-check", key = "#eoriNumber")
    public EoriCheck checkEori(String eoriNumber) {
        if (!onlineValidation) {
            return new EoriCheck(false, null, null, "EORI online validation disabled");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_XML);
            headers.set("SOAPAction", "validateEORI");

            HttpEntity<String> request = new HttpEntity<>(SOAP_BODY.formatted(eoriNumber), headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(baseUrl + "/validation", request, String.class);

            String body = resp.getBody();
            if (!resp.getStatusCode().is2xxSuccessful() || body == null) {
                log.warn("[EORI] Réponse inattendue {} pour {}", resp.getStatusCode(), eoriNumber);
                return new EoriCheck(false, null, null, "EORI online check failed");
            }

            Matcher validMatcher = VALID_PATTERN.matcher(body);
            if (!validMatcher.find()) {
                log.warn("[EORI] Réponse sans champ <valid> pour {}: {}", eoriNumber, body.substring(0, Math.min(300, body.length())));
                return new EoriCheck(false, null, null, "EORI online check failed");
            }

            boolean valid = Boolean.parseBoolean(validMatcher.group(1));
            String name = extract(TRADER_NAME_PATTERN, body);
            String address = joinAddress(extract(TRADER_STREET_PATTERN, body),
                extract(TRADER_POSTAL_PATTERN, body),
                extract(TRADER_CITY_PATTERN, body),
                extract(TRADER_COUNTRY_PATTERN, body));
            return new EoriCheck(valid, name, address, null);
        } catch (Exception e) {
            log.warn("[EORI] Échec vérification en ligne pour {}: {}", eoriNumber, e.getMessage());
            return new EoriCheck(false, null, null, "EORI online check failed");
        }
    }

    private String extract(Pattern pattern, String xml) {
        Matcher m = pattern.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private String joinAddress(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }
}
