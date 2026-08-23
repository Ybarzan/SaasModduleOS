package com.incokalk.security;

import com.incokalk.model.Company;
import com.incokalk.service.PlanChecker;
import com.incokalk.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/** Miroir de RolesAllowedAspect pour le palier commercial : verification live
 * en base (Company.plan, qui peut changer sans reconnexion via un webhook
 * Stripe) avec repli sur le claim "plan" du JWT si le contexte tenant est
 * absent -- meme schema exact que RolesAllowedAspect pour le role. */
@Aspect
@Component
@RequiredArgsConstructor
public class RequiresPlanAspect {

    private final PlanChecker planChecker;

    @Around("@annotation(requiresPlan)")
    public Object checkPlanOnMethod(ProceedingJoinPoint pjp, RequiresPlan requiresPlan) throws Throwable {
        return checkPlan(pjp, requiresPlan);
    }

    /** Annotation posee sur la classe (cas d'usage principal : un Hub entier,
     * ex. @RequiresPlan(ENTERPRISE) sur WarehouseController). Le pointcut
     * @within seul matcherait aussi les methodes qui portent en plus leur
     * propre @annotation(requiresPlan) -- exclu explicitement pour eviter
     * une double execution de l'advice sur ces methodes-la. */
    @Around("@within(requiresPlan) && !@annotation(com.incokalk.security.RequiresPlan) && execution(public * *(..))")
    public Object checkPlanOnClass(ProceedingJoinPoint pjp, RequiresPlan requiresPlan) throws Throwable {
        return checkPlan(pjp, requiresPlan);
    }

    private Object checkPlan(ProceedingJoinPoint pjp, RequiresPlan requiresPlan) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new SecurityException("No active HTTP request context");
        }
        HttpServletRequest request = attrs.getRequest();
        Company.Plan required = requiresPlan.value();

        UUID companyId = TenantContext.get();
        if (companyId != null && planChecker.hasMinimumPlan(companyId, required)) {
            return pjp.proceed();
        }

        String jwtPlan = (String) request.getAttribute("plan");
        if (jwtPlan != null) {
            // Parsing isole dans son propre try/catch : pjp.proceed() ne doit
            // JAMAIS s'executer a l'interieur de ce bloc, sinon une exception
            // metier legitime levee par la methode cible (ex. IllegalArgumentException
            // -> 400) serait avalee par erreur et remplacee par un 403.
            Company.Plan userPlan = null;
            try {
                userPlan = Company.Plan.valueOf(jwtPlan);
            } catch (IllegalArgumentException ignored) {
                // claim JWT invalide/inconnu -- traite comme insuffisant
            }
            if (userPlan != null && userPlan.ordinal() >= required.ordinal()) {
                return pjp.proceed();
            }
        }

        throw new SecurityException("Fonctionnalite non disponible sur votre plan actuel");
    }
}
