package com.incokalk.security;

import com.incokalk.model.Company;
import com.incokalk.service.PlanChecker;
import com.incokalk.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("RequiresPlanAspect — Tests unitaires")
class RequiresPlanAspectTest {

    PlanChecker planChecker;
    RequiresPlanAspect aspect;
    ProceedingJoinPoint pjp;
    RequiresPlan requiresPlan;
    HttpServletRequest request;
    MockedStatic<RequestContextHolder> requestContextHolder;
    UUID companyId;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        planChecker = mock(PlanChecker.class);
        aspect = new RequiresPlanAspect(planChecker);
        pjp = mock(ProceedingJoinPoint.class);
        request = mock(HttpServletRequest.class);
        companyId = UUID.randomUUID();

        requiresPlan = Sample.class.getAnnotation(RequiresPlan.class);

        ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);
        when(attrs.getRequest()).thenReturn(request);
        requestContextHolder = mockStatic(RequestContextHolder.class);
        requestContextHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(attrs);

        TenantContext.set(companyId);
    }

    @AfterEach
    void tearDown() {
        requestContextHolder.close();
        TenantContext.clear();
    }

    @RequiresPlan(Company.Plan.ENTERPRISE)
    private static class Sample {}

    @Test
    @DisplayName("Verification live en base suffisante → proceed")
    void liveCheckSufficient_proceeds() throws Throwable {
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.ENTERPRISE)).thenReturn(true);
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.checkPlanOnClass(pjp, requiresPlan);

        assertThat(result).isEqualTo("ok");
        verify(pjp).proceed();
    }

    @Test
    @DisplayName("Verification live insuffisante, claim JWT suffisant → repli, proceed")
    void liveCheckInsufficient_jwtFallbackSufficient_proceeds() throws Throwable {
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.ENTERPRISE)).thenReturn(false);
        when(request.getAttribute("plan")).thenReturn("ENTERPRISE");
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.checkPlanOnClass(pjp, requiresPlan);

        assertThat(result).isEqualTo("ok");
        verify(pjp).proceed();
    }

    @Test
    @DisplayName("Verification live insuffisante, claim JWT insuffisant → SecurityException, jamais proceed")
    void liveCheckInsufficient_jwtInsufficient_throws() throws Throwable {
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.ENTERPRISE)).thenReturn(false);
        when(request.getAttribute("plan")).thenReturn("STARTER");

        assertThatThrownBy(() -> aspect.checkPlanOnClass(pjp, requiresPlan))
            .isInstanceOf(SecurityException.class);
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("Claim JWT invalide/inconnu → traite comme insuffisant, SecurityException")
    void jwtPlanUnparseable_throws() throws Throwable {
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.ENTERPRISE)).thenReturn(false);
        when(request.getAttribute("plan")).thenReturn("GARBAGE");

        assertThatThrownBy(() -> aspect.checkPlanOnClass(pjp, requiresPlan))
            .isInstanceOf(SecurityException.class);
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("Exception metier levee par la methode cible (via repli JWT) → propagee telle quelle, pas avalee en 403")
    void businessExceptionFromProceed_propagatesUnchanged() throws Throwable {
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.ENTERPRISE)).thenReturn(false);
        when(request.getAttribute("plan")).thenReturn("ENTERPRISE");
        when(pjp.proceed()).thenThrow(new IllegalArgumentException("Stock insuffisant"));

        assertThatThrownBy(() -> aspect.checkPlanOnClass(pjp, requiresPlan))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Stock insuffisant");
    }

    @Test
    @DisplayName("Exception metier levee par la methode cible (via verification live) → propagee telle quelle")
    void businessExceptionFromProceed_viaLiveCheck_propagatesUnchanged() throws Throwable {
        when(planChecker.hasMinimumPlan(companyId, Company.Plan.ENTERPRISE)).thenReturn(true);
        when(pjp.proceed()).thenThrow(new IllegalArgumentException("Stock insuffisant"));

        assertThatThrownBy(() -> aspect.checkPlanOnClass(pjp, requiresPlan))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Stock insuffisant");
    }
}
