package com.fleethub.controller;

import com.fleethub.model.AdminIpAllowlist;
import com.fleethub.repository.AdminIpAllowlistRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ip-allowlist")
@RequiredArgsConstructor
@Tag(name = "Admin IP Allowlist", description = "Gestion des adresses IP autorisées pour l'admin plateforme")
public class AdminIpController {

    private final AdminIpAllowlistRepository repository;

    @GetMapping
    @Operation(summary = "Lister les IPs autorisées")
    public ResponseEntity<List<AdminIpAllowlist>> list() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    @Operation(summary = "Ajouter une IP")
    @Transactional
    public ResponseEntity<?> add(@RequestBody IpRequest body) {
        if (repository.existsByIpAddress(body.ip())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cette IP est déjà dans la liste"));
        }
        AdminIpAllowlist entry = AdminIpAllowlist.builder()
                .ipAddress(body.ip().trim())
                .label(body.label() != null ? body.label().trim() : null)
                .createdAt(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(repository.save(entry));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une IP")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "IP supprimée"));
    }

    public record IpRequest(@NotBlank String ip, String label) {}
}
