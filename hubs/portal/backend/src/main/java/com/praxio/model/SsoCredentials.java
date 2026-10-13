package com.praxio.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration singleton (une seule ligne, id toujours {@link #SINGLETON_ID})
 * des identifiants de connexion de l'opérateur pour chaque hub, utilisés par
 * le relais SSO (voir SsoService) : au clic sur "Ouvrir", Praxio appelle
 * POST {hub}/auth/login côté serveur avec ces identifiants pour obtenir un
 * jeton frais, puis redirige le navigateur déjà connecté. Jamais exposés via
 * l'API au-delà d'un simple booléen "configuré" (voir SsoCredentialsStatusDto).
 */
@Entity
@Table(name = "sso_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SsoCredentials {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    private String complianceEngineEmail;
    private String complianceEnginePassword;

    private String fleetHubEmail;
    private String fleetHubPassword;

    private String logistixEmail;
    private String logistixPassword;

    private String financeEmail;
    private String financePassword;
}
