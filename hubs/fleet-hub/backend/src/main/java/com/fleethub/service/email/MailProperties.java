package com.fleethub.service.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.mail")
@Getter
@Setter
public class MailProperties {

    private boolean enabled = false;
    private String host = "smtp.example.com";
    private int port = 587;
    private String username = "";
    private String password = "";
    private String from = "no-reply@fleethub.fr";
    private String baseUrl = "http://localhost:5199";
}
