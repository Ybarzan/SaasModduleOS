package com.incokalk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "incokalk.storage")
public class StorageConfig {

    private String endpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucketDocuments = "incokalk-documents";
    private String bucketLogos = "incokalk-logos";
    private String publicEndpoint = "http://localhost:9000";
}
