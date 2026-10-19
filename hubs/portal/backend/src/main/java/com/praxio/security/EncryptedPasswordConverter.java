package com.praxio.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Chiffre/déchiffre de façon transparente les mots de passe SSO (voir
 * SsoCredentials) au passage base <-> entité. @Autowired sur le champ plutôt
 * qu'un constructeur : Hibernate instancie lui-même les convertisseurs par un
 * constructeur sans argument, puis Spring (SpringBeanContainer, activé par
 * défaut par Spring Boot) injecte ce champ après coup.
 */
@Converter
public class EncryptedPasswordConverter implements AttributeConverter<String, String> {

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
