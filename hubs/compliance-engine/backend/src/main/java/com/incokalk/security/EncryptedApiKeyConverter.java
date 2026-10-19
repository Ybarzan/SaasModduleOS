package com.incokalk.security;

import com.incokalk.service.CredentialEncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Chiffre/déchiffre de façon transparente les clés d'intégration sortantes
 * (fleetHubApiKey, financeApiKey -- voir Company) au passage base <-> entité.
 * Ces clés sont collées par un admin depuis un AUTRE hub et renvoyées en clair
 * dans l'en-tête X-Internal-Integration-Key de chaque appel sortant : elles ne
 * sont jamais recherchées par égalité ici (aucune requête WHERE sur ce champ),
 * donc un IV aléatoire par chiffrement ne pose aucun problème -- contrairement
 * aux clés d'intégration ENTRANTES de ce même hub (vérifiées par égalité),
 * qui doivent rester hashées, jamais chiffrées de façon réversible.
 * Réutilise CredentialEncryptionService (déjà utilisé pour les mots de passe
 * IMAP), avec sa propre clé dédiée -- voir incokalk.security.mailbox-encryption.key.
 * @Autowired sur le champ plutôt qu'un constructeur : Hibernate instancie
 * lui-même les convertisseurs par un constructeur sans argument, puis Spring
 * (SpringBeanContainer, activé par défaut par Spring Boot) injecte ce champ.
 */
@Converter
public class EncryptedApiKeyConverter implements AttributeConverter<String, String> {

    @Autowired
    private CredentialEncryptionService encryptionService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptionService.decrypt(dbData);
    }
}
