package com.fleethub.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

/**
 * Sans convertisseur explicite, Hibernate stocke {@link YearMonth} par sérialisation Java
 * (colonne bytea) : illisible en base et faux pour tri/comparaison SQL. Le format ISO
 * "yyyy-MM" trie lexicographiquement comme chronologiquement, donc la colonne texte reste
 * correcte pour {@code ORDER BY} et les comparaisons {@code >}/{@code <}.
 */
@Converter(autoApply = true)
public class YearMonthConverter implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String dbData) {
        return dbData == null ? null : YearMonth.parse(dbData);
    }
}
