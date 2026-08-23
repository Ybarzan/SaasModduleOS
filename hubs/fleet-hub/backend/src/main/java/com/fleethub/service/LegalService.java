package com.fleethub.service;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.LegalContent;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mentions légales publiques (RGPD, art. 13) : conditions générales d'utilisation
 * et politique de confidentialité, accessibles sans authentification.
 */
@Service
public class LegalService {

    private static final String UPDATED = "2026-01-15";

    private final Map<String, LegalContent> contents = new LinkedHashMap<>();

    public LegalService() {
        contents.put("terms", new LegalContent("terms", "Conditions Générales d'Utilisation",
                UPDATED, termsHtml()));
        contents.put("privacy", new LegalContent("privacy", "Politique de Confidentialité",
                UPDATED, privacyHtml()));
    }

    public LegalContent get(String key) {
        LegalContent content = contents.get(key);
        if (content == null) {
            throw new ResourceNotFoundException("Document inconnu : " + key);
        }
        return content;
    }

    private String termsHtml() {
        return """
                <h2>1. Objet</h2>
                <p>Les présentes CGU régissent l'utilisation de la plateforme Fleet Hub, service SaaS de
                gestion de flotte (véhicules, chauffeurs, trajets, maintenance, conformité tachygraphe).</p>
                <h2>2. Compte et accès</h2>
                <p>L'ouverture d'un compte entraîne l'acceptation des présentes conditions. L'accès aux
                fonctionnalités dépend du rôle (ADMIN, GESTIONNAIRE) et de l'abonnement souscrit.</p>
                <h2>3. Obligations de l'utilisateur</h2>
                <p>L'utilisateur s'engage à fournir des informations exactes, à préserver la confidentialité
                de ses identifiants et à utiliser le service conformément à la réglementation.</p>
                <h2>4. Responsabilité</h2>
                <p>Fleet Hub fournit le service « en l'état ». Les données saisies (tachygraphes, GPS)
                doivent être vérifiées par l'utilisateur avant toute décision réglementaire.</p>
                <h2>5. Abonnement et facturation</h2>
                <p>Les abonnements sont souscrits en ligne (Stripe). Le défaut de paiement entraîne la
                suspension puis la résiliation du compte.</p>
                <h2>6. Résiliation</h2>
                <p>L'utilisateur peut résilier son compte à tout moment. Les données sont supprimées
                conformément à la politique de confidentialité, sauf obligations légales de conservation.</p>
                """;
    }

    private String privacyHtml() {
        return """
                <h2>1. Responsable de traitement</h2>
                <p>Fleet Hub est responsable des traitements de données personnelles réalisés dans le cadre
                du service. Contact : privacy@fleet-hub.fr.</p>
                <h2>2. Données collectées</h2>
                <p>Données de compte (nom, email), données des sociétés clientes (SIRET, coordonnées),
                données de flotte (chauffeurs, véhicules), données de conduite (tachygraphe, GPS) et traces
                d'audit.</p>
                <h2>3. Base légale</h2>
                <p>Contrat (art. 6.1.b), obligation légale (art. 6.1.c) et intérêt légitime (art. 6.1.f)
                pour la sécurité et la facturation.</p>
                <h2>4. Durées de conservation</h2>
                <p>Données de compte : durée de la relation. Données de conduite : 5 ans (réglementation
                tachygraphe UE 165/2014). Journaux d'audit : 3 ans.</p>
                <h2>5. Vos droits</h2>
                <p>Accès, rectification, effacement, portabilité et opposition : exercez-les via le tableau
                de bord (export / suppression du compte) ou par email à privacy@fleet-hub.fr. Réponse sous 30 jours.</p>
                <h2>6. Sous-traitants</h2>
                <p>Hébergement en Union européenne, Stripe (paiement) et fournisseur d'email transactionnel.
                Les données ne sont jamais vendues à des tiers.</p>
                <h2>7. Cookies et sécurité</h2>
                <p>Jeton JWT en mémoire de session, chiffrement des mots de passe (BCrypt), connexion
                HTTPS et limitation des tentatives de connexion.</p>
                """;
    }
}
