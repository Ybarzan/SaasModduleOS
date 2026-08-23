package com.incokalk.controller.training;

import com.incokalk.model.CompanyRole;
import com.incokalk.model.CourseEnrollment;
import com.incokalk.model.TrainingModule;
import com.incokalk.security.RolesAllowed;
import com.incokalk.service.TrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/academy")
@RequiredArgsConstructor
@Tag(name = "Academy", description = "IncoKalk Academy — formation et certification Incoterms")
public class TrainingController {

    private final TrainingService trainingService;

    @GetMapping("/modules")
    @RolesAllowed({})
    @Operation(summary = "Catalogue des modules publiés")
    public ResponseEntity<?> getCatalog() {
        return ResponseEntity.ok(trainingService.getCatalog());
    }

    @GetMapping("/modules/{id}")
    @RolesAllowed({})
    @Operation(summary = "Détail d'un module avec statut d'inscription")
    public ResponseEntity<?> getModule(@PathVariable UUID id) {
        return ResponseEntity.ok(trainingService.getModuleContent(id));
    }

    @PostMapping("/modules/{id}/enroll")
    @RolesAllowed({})
    @Operation(summary = "S'inscrire à un module")
    public ResponseEntity<CourseEnrollment> enroll(@PathVariable UUID id, @RequestParam UUID userId) {
        return ResponseEntity.ok(trainingService.enroll(id, userId));
    }

    @PutMapping("/enrollments/{id}/progress")
    @RolesAllowed({})
    @Operation(summary = "Mettre à jour la progression")
    public ResponseEntity<CourseEnrollment> updateProgress(@PathVariable UUID id, @RequestParam int percent) {
        return ResponseEntity.ok(trainingService.updateProgress(id, percent));
    }

    @PostMapping("/modules/{moduleId}/quiz")
    @RolesAllowed({})
    @Operation(summary = "Soumettre un quiz")
    public ResponseEntity<?> submitQuiz(@PathVariable UUID moduleId, @RequestParam UUID userId,
                                        @RequestBody Map<String, String> answers) {
        return ResponseEntity.ok(trainingService.submitQuiz(moduleId, userId, answers));
    }

    @GetMapping("/enrollments/{id}/certificate")
    @RolesAllowed({})
    @Operation(summary = "Obtenir le certificat")
    public ResponseEntity<?> getCertificate(@PathVariable UUID id) {
        return ResponseEntity.ok(trainingService.getCertification(id));
    }

    @GetMapping("/dashboard")
    @RolesAllowed({})
    @Operation(summary = "Tableau de bord de l'académie")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(trainingService.getDashboard());
    }

    @PostMapping("/modules")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Créer un module (admin)")
    public ResponseEntity<TrainingModule> createModule(@Valid @RequestBody TrainingModule module) {
        return ResponseEntity.ok(trainingService.createModule(module));
    }

    @PutMapping("/modules/{id}")
    @RolesAllowed({CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN})
    @Operation(summary = "Mettre à jour un module (admin)")
    public ResponseEntity<TrainingModule> updateModule(@PathVariable UUID id, @Valid @RequestBody TrainingModule module) {
        return ResponseEntity.ok(trainingService.updateModule(id, module));
    }
}
