package com.incokalk.aspect;

import com.incokalk.model.ShipmentOrder;
import com.incokalk.service.AuditLogService;
import com.incokalk.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around("execution(* com.incokalk.controller.shipment.ShipmentController.createShipment(..))")
    public Object auditShipmentCreated(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            if (result instanceof ShipmentOrder shipment) {
                HttpServletRequest request = getCurrentRequest();
                UUID userId = request != null ? (UUID) request.getAttribute("userId") : null;
                String userEmail = request != null ? (String) request.getAttribute("userEmail") : null;
                String userRole = request != null ? (String) request.getAttribute("userRole") : null;
                UUID companyId = TenantContext.get();

                auditLogService.log(
                        companyId, userId, userEmail, userRole,
                        "SHIPMENT_CREATED", "SHIPMENT", shipment.getId(),
                        shipment.getOrderNumber(),
                        "Commande créée: " + shipment.getOrderNumber(),
                        getRequestIp(request), getRequestUserAgent(request)
                );
            }
        } catch (Exception e) {
            log.warn("Audit log failed for shipment creation: {}", e.getMessage());
        }
        return result;
    }

    @Around("execution(* com.incokalk.controller.shipment.ShipmentController.updateStatus(..))")
    public Object auditShipmentStatusChanged(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            if (result instanceof ShipmentOrder shipment) {
                HttpServletRequest request = getCurrentRequest();
                UUID userId = request != null ? (UUID) request.getAttribute("userId") : null;
                String userEmail = request != null ? (String) request.getAttribute("userEmail") : null;
                String userRole = request != null ? (String) request.getAttribute("userRole") : null;
                UUID companyId = TenantContext.get();

                auditLogService.log(
                        companyId, userId, userEmail, userRole,
                        "SHIPMENT_STATUS_CHANGED", "SHIPMENT", shipment.getId(),
                        shipment.getOrderNumber(),
                        "Statut mis à jour: " + shipment.getStatus(),
                        getRequestIp(request), getRequestUserAgent(request)
                );
            }
        } catch (Exception e) {
            log.warn("Audit log failed for shipment status change: {}", e.getMessage());
        }
        return result;
    }

    @Around("execution(* com.incokalk.controller.shipment.ShipmentController.deleteShipment(..))")
    public Object auditShipmentDeleted(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        UUID userId = request != null ? (UUID) request.getAttribute("userId") : null;
        String userEmail = request != null ? (String) request.getAttribute("userEmail") : null;
        String userRole = request != null ? (String) request.getAttribute("userRole") : null;
        UUID companyId = TenantContext.get();

        Object[] args = joinPoint.getArgs();
        UUID shipmentId = null;
        if (args.length > 0 && args[0] instanceof UUID uuid) {
            shipmentId = uuid;
        }

        Object result = joinPoint.proceed();
        try {
            auditLogService.log(
                    companyId, userId, userEmail, userRole,
                    "SHIPMENT_DELETED", "SHIPMENT", shipmentId,
                    shipmentId != null ? shipmentId.toString() : null,
                    "Commande supprimée",
                    getRequestIp(request), getRequestUserAgent(request)
            );
        } catch (Exception e) {
            log.warn("Audit log failed for shipment deletion: {}", e.getMessage());
        }
        return result;
    }

    @Around("execution(* com.incokalk.controller.shipment.CarrierController.createCarrier(..))")
    public Object auditCarrierCreated(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            if (result instanceof com.incokalk.model.Carrier carrier) {
                HttpServletRequest request = getCurrentRequest();
                UUID userId = request != null ? (UUID) request.getAttribute("userId") : null;
                String userEmail = request != null ? (String) request.getAttribute("userEmail") : null;
                String userRole = request != null ? (String) request.getAttribute("userRole") : null;
                UUID companyId = TenantContext.get();

                auditLogService.log(
                        companyId, userId, userEmail, userRole,
                        "CARRIER_CREATED", "CARRIER", carrier.getId(),
                        carrier.getName(),
                        "Transporteur créé: " + carrier.getName(),
                        getRequestIp(request), getRequestUserAgent(request)
                );
            }
        } catch (Exception e) {
            log.warn("Audit log failed for carrier creation: {}", e.getMessage());
        }
        return result;
    }

    @Around("execution(* com.incokalk.controller.shipment.CarrierController.updateCarrier(..))")
    public Object auditCarrierUpdated(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            if (result instanceof com.incokalk.model.Carrier carrier) {
                HttpServletRequest request = getCurrentRequest();
                UUID userId = request != null ? (UUID) request.getAttribute("userId") : null;
                String userEmail = request != null ? (String) request.getAttribute("userEmail") : null;
                String userRole = request != null ? (String) request.getAttribute("userRole") : null;
                UUID companyId = TenantContext.get();

                auditLogService.log(
                        companyId, userId, userEmail, userRole,
                        "CARRIER_UPDATED", "CARRIER", carrier.getId(),
                        carrier.getName(),
                        "Transporteur mis à jour: " + carrier.getName(),
                        getRequestIp(request), getRequestUserAgent(request)
                );
            }
        } catch (Exception e) {
            log.warn("Audit log failed for carrier update: {}", e.getMessage());
        }
        return result;
    }

    @Around("execution(* com.incokalk.controller.shipment.CarrierController.deleteCarrier(..))")
    public Object auditCarrierDeleted(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        UUID userId = request != null ? (UUID) request.getAttribute("userId") : null;
        String userEmail = request != null ? (String) request.getAttribute("userEmail") : null;
        String userRole = request != null ? (String) request.getAttribute("userRole") : null;
        UUID companyId = TenantContext.get();

        Object[] args = joinPoint.getArgs();
        UUID carrierId = null;
        if (args.length > 0 && args[0] instanceof UUID uuid) {
            carrierId = uuid;
        }

        Object result = joinPoint.proceed();
        try {
            auditLogService.log(
                    companyId, userId, userEmail, userRole,
                    "CARRIER_DELETED", "CARRIER", carrierId,
                    carrierId != null ? carrierId.toString() : null,
                    "Transporteur supprimé",
                    getRequestIp(request), getRequestUserAgent(request)
            );
        } catch (Exception e) {
            log.warn("Audit log failed for carrier deletion: {}", e.getMessage());
        }
        return result;
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getRequestIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getRequestUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        return ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua;
    }
}
