package com.incokalk.controller;

import com.incokalk.model.CompanyRole;
import com.incokalk.model.CourseEnrollment;
import com.incokalk.model.TrainingModule;
import com.incokalk.service.TrainingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrainingControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrainingService trainingService;

    @Test
    @DisplayName("GET /v1/academy/modules → catalogue des modules")
    void getCatalog() throws Exception {
        TrainingModule module = TrainingModule.builder()
                .id(UUID.randomUUID())
                .title("Incoterms 2020 Fundamentals")
                .category(TrainingModule.Category.INCOTERMS)
                .difficulty(TrainingModule.Difficulty.BEGINNER)
                .isPublished(true)
                .build();

        when(trainingService.getCatalog()).thenReturn(List.of(module));

        mockMvc.perform(get("/v1/academy/modules")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Incoterms 2020 Fundamentals"))
                .andExpect(jsonPath("$[0].category").value("INCOTERMS"));
    }

    @Test
    @DisplayName("GET /v1/academy/modules/{id} → détail d'un module")
    void getModule() throws Exception {
        UUID moduleId = UUID.randomUUID();
        Map<String, Object> content = new java.util.HashMap<>(Map.of(
                "module", TrainingModule.builder().id(moduleId).title("Test Module").build()
        ));
        content.put("enrollment", null);

        when(trainingService.getModuleContent(moduleId)).thenReturn(content);

        mockMvc.perform(get("/v1/academy/modules/" + moduleId)
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.module.title").value("Test Module"));
    }

    @Test
    @DisplayName("POST /v1/academy/modules/{id}/enroll → s'inscrire à un module")
    void enroll() throws Exception {
        UUID moduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(UUID.randomUUID())
                .moduleId(moduleId)
                .userId(userId)
                .status(CourseEnrollment.Status.NOT_STARTED)
                .build();

        when(trainingService.enroll(moduleId, userId)).thenReturn(enrollment);

        mockMvc.perform(post("/v1/academy/modules/" + moduleId + "/enroll")
                        .header("Authorization", authHeader())
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleId").value(moduleId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("PUT /v1/academy/enrollments/{id}/progress → mise à jour progression")
    void updateProgress() throws Exception {
        UUID enrollmentId = UUID.randomUUID();
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(enrollmentId)
                .progressPercent(50)
                .status(CourseEnrollment.Status.IN_PROGRESS)
                .build();

        when(trainingService.updateProgress(enrollmentId, 50)).thenReturn(enrollment);

        mockMvc.perform(put("/v1/academy/enrollments/" + enrollmentId + "/progress")
                        .header("Authorization", authHeader())
                        .param("percent", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressPercent").value(50));
    }

    @Test
    @DisplayName("POST /v1/academy/modules/{moduleId}/quiz → soumettre un quiz")
    void submitQuiz() throws Exception {
        UUID moduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> result = Map.of(
                "score", 80,
                "passed", true,
                "correct", 4,
                "total", 5
        );

        when(trainingService.submitQuiz(eq(moduleId), eq(userId), any(Map.class))).thenReturn(result);

        mockMvc.perform(post("/v1/academy/modules/" + moduleId + "/quiz")
                        .header("Authorization", authHeader())
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"q1\":\"A\",\"q2\":\"B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(80))
                .andExpect(jsonPath("$.passed").value(true));
    }

    @Test
    @DisplayName("GET /v1/academy/enrollments/{id}/certificate → obtenir certificat")
    void getCertificate() throws Exception {
        UUID enrollmentId = UUID.randomUUID();
        Map<String, Object> cert = Map.of(
                "certificateNumber", "INK-ACADEMY-2026-ABC123",
                "moduleTitle", "Incoterms 2020 Fundamentals",
                "score", 95,
                "status", "VALID"
        );

        when(trainingService.getCertification(enrollmentId)).thenReturn(cert);

        mockMvc.perform(get("/v1/academy/enrollments/" + enrollmentId + "/certificate")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificateNumber").value("INK-ACADEMY-2026-ABC123"));
    }

    @Test
    @DisplayName("GET /v1/academy/dashboard → tableau de bord")
    void getDashboard() throws Exception {
        when(trainingService.getDashboard()).thenReturn(Map.of(
                "totalEnrolled", 150L,
                "completed", 85L,
                "inProgress", 45L,
                "failed", 20L,
                "passRate", 56.67
        ));

        mockMvc.perform(get("/v1/academy/dashboard")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEnrolled").value(150))
                .andExpect(jsonPath("$.passRate").value(56.67));
    }

    @Test
    @DisplayName("POST /v1/academy/modules → créer un module (admin)")
    void createModule() throws Exception {
        TrainingModule module = TrainingModule.builder()
                .id(UUID.randomUUID())
                .title("Nouveau Module")
                .category(TrainingModule.Category.COMPLIANCE)
                .difficulty(TrainingModule.Difficulty.ADVANCED)
                .build();

        when(trainingService.createModule(any(TrainingModule.class))).thenReturn(module);

        mockMvc.perform(post("/v1/academy/modules")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nouveau Module\",\"category\":\"COMPLIANCE\",\"difficulty\":\"ADVANCED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Nouveau Module"));
    }

    @Test
    @DisplayName("PUT /v1/academy/modules/{id} → mettre à jour un module (admin)")
    void updateModule() throws Exception {
        UUID moduleId = UUID.randomUUID();
        TrainingModule updated = TrainingModule.builder()
                .id(moduleId)
                .title("Module Mis à Jour")
                .category(TrainingModule.Category.INCOTERMS)
                .difficulty(TrainingModule.Difficulty.ADVANCED)
                .build();

        when(trainingService.updateModule(eq(moduleId), any(TrainingModule.class))).thenReturn(updated);

        mockMvc.perform(put("/v1/academy/modules/" + moduleId)
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Module Mis à Jour\",\"category\":\"INCOTERMS\",\"difficulty\":\"ADVANCED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Module Mis à Jour"));
    }

    @Test
    @DisplayName("POST /v1/academy/modules → 403 si le rôle est insuffisant (MANAGER)")
    void createModule_forbidden() throws Exception {
        String managerToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);

        mockMvc.perform(post("/v1/academy/modules")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nouveau Module\",\"category\":\"COMPLIANCE\",\"difficulty\":\"ADVANCED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /v1/academy/modules/{id} → 403 si le rôle est insuffisant (MANAGER)")
    void updateModule_forbidden() throws Exception {
        UUID moduleId = UUID.randomUUID();
        String managerToken = generateJwtToken(userId, companyId, CompanyRole.Role.MANAGER);

        mockMvc.perform(put("/v1/academy/modules/" + moduleId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Module Mis à Jour\",\"category\":\"INCOTERMS\",\"difficulty\":\"ADVANCED\"}"))
                .andExpect(status().isForbidden());
    }
}