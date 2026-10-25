package com.praxio;

import com.praxio.config.HubUrlsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(HubUrlsProperties.class)
public class PraxioApplication {
    public static void main(String[] args) {
        SpringApplication.run(PraxioApplication.class, args);
    }
}
