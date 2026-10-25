package com.praxio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praxio.config.HubUrlsProperties;
import com.praxio.dto.SsoLaunchResultDto;
import com.praxio.dto.sso.ComplianceEngineLoginResponse;
import com.praxio.dto.sso.EmailLoginRequest;
import com.praxio.dto.sso.FinanceLoginResponse;
import com.praxio.dto.sso.FleetHubLoginResponse;
import com.praxio.dto.sso.LogistixLoginResponse;
import com.praxio.dto.sso.UsernameLoginRequest;
import com.praxio.model.SsoCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

/**
 * Relais SSO ("credential relay") : au clic sur "Ouvrir" dans Praxio, on
 * appelle POST {hub}/auth/login côté serveur avec les identifiants stockés
 * (voir SsoCredentialsService), puis on renvoie une URL de redirection vers
 * le frontend du hub avec la réponse de connexion encodée dans le fragment
 * (#, jamais la requête ?) -- un fragment n'est jamais envoyé au serveur ni
 * journalisé, seul le navigateur y accède côté client (voir chaque page
 * /sso-entry de chaque hub, qui décode ce fragment pour se connecter sans
 * ressaisir les identifiants).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoService {

    private final RestTemplate restTemplate;
    private final SsoCredentialsService ssoCredentialsService;
    private final HubUrlsProperties hubUrls;
    private final ObjectMapper objectMapper;

    public SsoLaunchResultDto launch(String hub) {
        SsoCredentials creds = ssoCredentialsService.getCredentialsOrEmpty();
        return switch (hub) {
            case "complianceEngine" -> launchComplianceEngine(creds);
            case "fleetHub" -> launchFleetHub(creds);
            case "logistix" -> launchLogistix(creds);
            case "finance" -> launchFinance(creds);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hub inconnu : " + hub);
        };
    }

    private SsoLaunchResultDto launchComplianceEngine(SsoCredentials creds) {
        requireConfigured(creds.getComplianceEngineEmail(), creds.getComplianceEnginePassword(), "compliance-engine");
        ComplianceEngineLoginResponse login = callLogin(
                hubUrls.complianceEngineUrl() + "/api/v1/auth/login",
                new EmailLoginRequest(creds.getComplianceEngineEmail(), creds.getComplianceEnginePassword()),
                ComplianceEngineLoginResponse.class,
                "compliance-engine");
        return redirect(hubUrls.complianceEngineFrontendUrl(), login);
    }

    private SsoLaunchResultDto launchFleetHub(SsoCredentials creds) {
        requireConfigured(creds.getFleetHubEmail(), creds.getFleetHubPassword(), "fleet-hub");
        FleetHubLoginResponse login = callLogin(
                hubUrls.fleetHubUrl() + "/api/auth/login",
                new UsernameLoginRequest(creds.getFleetHubEmail(), creds.getFleetHubPassword()),
                FleetHubLoginResponse.class,
                "fleet-hub");
        if (login.totpRequired()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce compte fleet-hub nécessite une validation à deux facteurs : connexion automatique impossible, connectez-vous manuellement.");
        }
        return redirect(hubUrls.fleetHubFrontendUrl(), login);
    }

    private SsoLaunchResultDto launchLogistix(SsoCredentials creds) {
        requireConfigured(creds.getLogistixEmail(), creds.getLogistixPassword(), "logistix");
        LogistixLoginResponse login = callLogin(
                hubUrls.logistixUrl() + "/v1/auth/login",
                new EmailLoginRequest(creds.getLogistixEmail(), creds.getLogistixPassword()),
                LogistixLoginResponse.class,
                "logistix");
        return redirect(hubUrls.logistixFrontendUrl(), login);
    }

    private SsoLaunchResultDto launchFinance(SsoCredentials creds) {
        requireConfigured(creds.getFinanceEmail(), creds.getFinancePassword(), "finance");
        FinanceLoginResponse login = callLogin(
                hubUrls.financeUrl() + "/v1/auth/login",
                new EmailLoginRequest(creds.getFinanceEmail(), creds.getFinancePassword()),
                FinanceLoginResponse.class,
                "finance");
        return redirect(hubUrls.financeFrontendUrl(), login);
    }

    private <TReq, TRes> TRes callLogin(String url, TReq requestBody, Class<TRes> responseType, String hubName) {
        try {
            TRes body = restTemplate.postForObject(url, requestBody, responseType);
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Réponse vide de " + hubName);
            }
            return body;
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Identifiants invalides pour " + hubName + " -- vérifiez les identifiants SSO dans les paramètres.");
        } catch (RestClientException e) {
            log.warn("[SSO] Connexion {} indisponible ({}) : {}", hubName, url, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, hubName + " est injoignable.");
        }
    }

    private SsoLaunchResultDto redirect(String frontendUrl, Object loginResponse) {
        return new SsoLaunchResultDto(stripTrailingSlash(frontendUrl) + "/sso-entry#" + encode(loginResponse));
    }

    /**
     * Base64 URL-safe sans padding : alphabet uniquement composé de
     * caractères valides dans un fragment d'URL sans aucun échappement, donc
     * aucune ambiguïté de découpage côté page /sso-entry.
     */
    private String encode(Object value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible de préparer la redirection SSO");
        }
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private void requireConfigured(String email, String password, String hubName) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun identifiant SSO enregistré pour " + hubName + " -- voir Paramètres.");
        }
    }
}
