package com.fleethub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FleethubApplication {
    public static void main(String[] args) {
        SpringApplication.run(FleethubApplication.class, args);
    }
}
