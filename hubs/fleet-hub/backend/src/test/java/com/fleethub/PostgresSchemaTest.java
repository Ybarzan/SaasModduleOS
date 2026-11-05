package com.fleethub;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migrations Flyway jouées sur un vrai PostgreSQL, puis validation du schéma par
 * Hibernate ({@code ddl-auto=validate}), comme en production. Les autres tests
 * tournent sur H2 avec un schéma généré par Hibernate : sans celui-ci, une migration
 * cassée ou un écart entité/SQL n'apparaissait qu'au démarrage en production.
 * <p>
 * Ne s'exécute que si {@code FLEETHUB_PG_URL} est défini (job « Backend (PostgreSQL) »
 * de la CI, ou en local : {@code FLEETHUB_PG_URL=jdbc:postgresql://localhost:5432/fleethub_ci
 * FLEETHUB_PG_USER=… FLEETHUB_PG_PASSWORD=… mvn test -Dtest=PostgresSchemaTest}). La base
 * doit être vide.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FLEETHUB_PG_URL", matches = "jdbc:postgresql:.+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresSchemaTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FLEETHUB_PG_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("FLEETHUB_PG_USER", "fleethub"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("FLEETHUB_PG_PASSWORD", "fleethub"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void everyMigrationIsApplied_andTheSchemaMatchesTheEntities() throws Exception {
        // Le contexte n'a pu démarrer que si Flyway a tout appliqué et si Hibernate a validé le schéma.
        Resource[] migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__*.sql");
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class);
        assertEquals(migrations.length, applied, "chaque fichier V*__*.sql doit être appliqué");
    }

    @Test
    void demoDataIsSeeded_onTheRealSchema() {
        // Le jeu de démo (app.seed.enabled=true hors prod) exerce les contraintes réelles :
        // CHECK, clés étrangères, unicité par société (V26).
        Integer trucks = jdbcTemplate.queryForObject("SELECT count(*) FROM truck", Integer.class);
        assertTrue(trucks != null && trucks > 0, "les véhicules de démo doivent être insérés sans erreur");
    }
}
