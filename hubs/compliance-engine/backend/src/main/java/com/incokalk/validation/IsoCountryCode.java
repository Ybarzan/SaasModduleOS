package com.incokalk.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsoCountryCodeValidator.class)
public @interface IsoCountryCode {
    String message() default "Code pays invalide (attendu : code ISO 3166-1 alpha-2, ex. FR, US, CN)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
