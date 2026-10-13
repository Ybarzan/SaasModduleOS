package com.finance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Vérifie que le contexte Spring démarre correctement : câblage JPA/Flyway
 * (migrations H2 en dev), Security (filtres JWT/Tenant), et tous les beans
 * de service/contrôleur.
 */
@SpringBootTest
class FinanceApplicationTests {

    @Test
    void contextLoads() {
    }
}
