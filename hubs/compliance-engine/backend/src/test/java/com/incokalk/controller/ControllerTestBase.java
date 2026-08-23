package com.incokalk.controller;

import com.incokalk.model.CompanyRole;
import com.incokalk.security.JwtService;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ControllerTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtService jwtService;

    protected UUID companyId = UUID.randomUUID();
    protected UUID userId = UUID.randomUUID();
    protected String jwtToken;

    @BeforeEach
    void setUp() {
        TenantContext.set(companyId);
        jwtToken = generateJwtToken(userId, companyId, CompanyRole.Role.OWNER);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // Délègue au bean JwtService réel pour signer avec le secret effectivement actif
    // (évite tout hardcode qui divergerait d'une variable d'env JWT_SECRET locale/CI).
    // Plan ENTERPRISE (palier max) par defaut -- meme logique que Role.OWNER plus bas :
    // les tests exercent la logique metier des endpoints, pas le verrouillage par plan
    // (RequiresPlanAspect), sauf a le tester explicitement avec un plan moindre.
    protected String generateJwtToken(UUID userId, UUID companyId, CompanyRole.Role role) {
        return jwtService.generateToken(userId, "test@example.com", "ENTERPRISE", role.name());
    }

    protected String authHeader() {
        return "Bearer " + jwtToken;
    }
}