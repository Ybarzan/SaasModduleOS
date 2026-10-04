package com.fleethub.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Résout l'adresse IP réelle du client à partir de {@code X-Forwarded-For}, en tenant
 * compte du nombre de reverse proxies de confiance devant le backend.
 *
 * <p>Topologie de déploiement (docker-compose.yml) : client -&gt; Caddy -&gt; nginx -&gt; backend.
 * Caddy et nginx <b>ajoutent</b> chacun leur propre vue de l'adresse distante en fin de
 * l'en-tête plutôt que de le remplacer (nginx : {@code $proxy_add_x_forwarded_for}). Un
 * client malveillant contrôle donc entièrement le préfixe de l'en-tête, mais pas les
 * {@code trustedProxyHops} dernières valeurs, qui sont écrites par les proxies eux-mêmes.
 * L'adresse fiable est donc celle écrite par le premier proxy de confiance (Caddy), soit
 * la valeur à {@code hops.length - trustedProxyHops} en partant de la gauche — jamais la
 * première valeur de la liste, qui est falsifiable à volonté par le client.</p>
 */
@Component
public class ClientIpResolver {

    @Value("${app.security.trusted-proxy-hops:2}")
    private int trustedProxyHops;

    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] hops = forwarded.split(",");
        int trustedIndex = hops.length - trustedProxyHops;
        if (trustedIndex < 0) {
            // En-tête plus court que le nombre de proxies attendu : configuration
            // incohérente avec la topologie réelle, on ne fait pas confiance au client.
            return request.getRemoteAddr();
        }
        return hops[trustedIndex].trim();
    }
}
