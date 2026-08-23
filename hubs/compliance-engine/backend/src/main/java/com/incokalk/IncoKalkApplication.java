package com.incokalk;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@OpenAPIDefinition(info = @Info(
    title = "IncoKalk API", version = "1.0.0",
    description = "Simulateur Incoterms® 2020 — calcul du coût total acheteur"
))
@SecuritySchemes({
    @SecurityScheme(name = "ApiKey", type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER, paramName = "X-API-Key"),
    @SecurityScheme(name = "BearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer")
})
public class IncoKalkApplication {
    public static void main(String[] args) {
        SpringApplication.run(IncoKalkApplication.class, args);
    }
}
