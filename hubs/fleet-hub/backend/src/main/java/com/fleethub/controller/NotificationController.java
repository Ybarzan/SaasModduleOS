package com.fleethub.controller;

import com.fleethub.dto.NotificationDto;
import com.fleethub.dto.NotificationRuleDto;
import com.fleethub.security.TenantContext;
import com.fleethub.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Alertes & notifications du tenant : liste (avec balayage automatique),
 * marquage « lu », configuration des règles d'alerte.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Gestion des alertes et notifications de la flotte")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Lister les notifications", description = "Retourne la liste des notifications avec balayage automatique des nouvelles alertes")
    @ApiResponse(responseCode = "200", description = "Liste retournée avec succès")
    public List<NotificationDto> list() {
        return notificationService.listAndScan(TenantContext.require().getId());
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Nombre de non-lues", description = "Retourne le nombre de notifications non lues")
    @ApiResponse(responseCode = "200", description = "Compteur retourné avec succès")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.unreadCount(TenantContext.require().getId()));
    }

    @PostMapping("/scan")
    @Operation(summary = "Scanner les alertes", description = "Déclenche un balayage des conditions d'alerte et crée les notifications correspondantes")
    @ApiResponse(responseCode = "200", description = "Scan effectué avec succès")
    public Map<String, Integer> scan() {
        return Map.of("created", notificationService.scan(TenantContext.require().getId()));
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marquer comme lu", description = "Marque une notification comme lue")
    @ApiResponse(responseCode = "204", description = "Notification marquée comme lue")
    @ApiResponse(responseCode = "404", description = "Notification introuvable")
    public void markRead(@PathVariable Long id) {
        notificationService.markRead(TenantContext.require().getId(), id);
    }

    @GetMapping("/rules")
    @Operation(summary = "Lister les règles d'alerte", description = "Retourne la liste des règles de notification configurées")
    @ApiResponse(responseCode = "200", description = "Liste retournée avec succès")
    public List<NotificationRuleDto> rules() {
        return notificationService.listRules(TenantContext.require().getId());
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une règle d'alerte", description = "Ajoute ou met à jour une règle de notification")
    @ApiResponse(responseCode = "201", description = "Règle créée avec succès")
    @ApiResponse(responseCode = "400", description = "Données de la règle invalides")
    public NotificationRuleDto saveRule(@Valid @RequestBody NotificationRuleDto dto) {
        return notificationService.saveRule(TenantContext.require().getId(), dto);
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une règle d'alerte", description = "Supprime une règle de notification")
    @ApiResponse(responseCode = "204", description = "Règle supprimée avec succès")
    @ApiResponse(responseCode = "404", description = "Règle introuvable")
    public void deleteRule(@PathVariable Long id) {
        notificationService.deleteRule(TenantContext.require().getId(), id);
    }
}
