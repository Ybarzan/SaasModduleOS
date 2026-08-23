package com.incokalk.security;

import com.incokalk.model.CompanyRole;
import com.incokalk.service.RoleChecker;
import com.incokalk.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class RolesAllowedAspect {

    private final RoleChecker roleChecker;

    @Around("@annotation(rolesAllowed)")
    public Object checkRoles(ProceedingJoinPoint pjp, RolesAllowed rolesAllowed) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new SecurityException("No active HTTP request context");
        }
        HttpServletRequest request = attrs.getRequest();
        UUID userId = (UUID) request.getAttribute("userId");
        if (userId == null) {
            throw new SecurityException("User not authenticated");
        }
        CompanyRole.Role[] required = rolesAllowed.value();
        if (required.length == 0) {
            return pjp.proceed();
        }
        UUID companyId = TenantContext.get();
        if (companyId == null) {
            String jwtRole = (String) request.getAttribute("role");
            if (jwtRole != null) {
                CompanyRole.Role userRole = CompanyRole.Role.valueOf(jwtRole);
                for (CompanyRole.Role r : required) {
                    if (userRole == r) {
                        return pjp.proceed();
                    }
                }
            }
            throw new SecurityException("No tenant context");
        }
        for (CompanyRole.Role role : required) {
            if (roleChecker.hasRole(userId, companyId, role)) {
                return pjp.proceed();
            }
        }
        String jwtRole = (String) request.getAttribute("role");
        if (jwtRole != null) {
            CompanyRole.Role userRole = CompanyRole.Role.valueOf(jwtRole);
            for (CompanyRole.Role r : required) {
                if (userRole == r) {
                    return pjp.proceed();
                }
            }
        }
        throw new SecurityException("Insufficient privileges for tenant");
    }
}