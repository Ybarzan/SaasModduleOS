package com.fleethub.billing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration Stripe (variables d'environnement, voir .env.example).
 */
@Component
@ConfigurationProperties(prefix = "stripe")
@Getter
@Setter
public class StripeProperties {

    private boolean enabled = false;
    private String secretKey = "";
    private String webhookSecret = "";
    private String priceStarter = "";
    private String pricePro = "";
    private String priceEnterprise = "";
    private String successUrl = "http://localhost:5199/billing";
    private String cancelUrl = "http://localhost:5199/billing";
    private String portalReturnUrl = "http://localhost:5199/billing";
}
