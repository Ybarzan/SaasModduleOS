package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.CourseEnrollment;
import com.incokalk.model.QuizAttempt;
import com.incokalk.model.TrainingModule;
import com.incokalk.repository.CourseEnrollmentRepository;
import com.incokalk.repository.QuizAttemptRepository;
import com.incokalk.repository.TrainingModuleRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TrainingService — Tests unitaires")
class TrainingServiceTest {

    TrainingService service;
    TrainingModuleRepository moduleRepo;
    CourseEnrollmentRepository enrollmentRepo;
    QuizAttemptRepository quizAttemptRepo;

    @BeforeEach
    void setUp() {
        moduleRepo = mock(TrainingModuleRepository.class);
        enrollmentRepo = mock(CourseEnrollmentRepository.class);
        quizAttemptRepo = mock(QuizAttemptRepository.class);
        service = new TrainingService(moduleRepo, enrollmentRepo, quizAttemptRepo);
        TenantContext.set(UUID.randomUUID());
    }

    @Test
    @DisplayName("getCatalog → retourne les modules publiés")
    void getCatalog() {
        List<TrainingModule> modules = List.of(
                TrainingModule.builder().title("Module 1").isPublished(true).build(),
                TrainingModule.builder().title("Module 2").isPublished(true).build()
        );
        when(moduleRepo.findByIsPublishedTrueOrderByCreatedAtDesc()).thenReturn(modules);

        List<TrainingModule> result = service.getCatalog();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getModuleContent → module trouvé")
    void getModuleContent_found() {
        UUID moduleId = UUID.randomUUID();
        TrainingModule module = TrainingModule.builder().id(moduleId).title("Test Module").build();
        when(moduleRepo.findById(moduleId)).thenReturn(Optional.of(module));
        when(enrollmentRepo.findByCompanyIdAndUserIdAndModuleId(any(), any(), eq(moduleId)))
                .thenReturn(Optional.empty());

        Map<String, Object> content = service.getModuleContent(moduleId);

        assertThat(content).containsKey("module");
        assertThat(content).doesNotContainKey("enrollment");
    }

    @Test
    @DisplayName("getModuleContent → module non trouvé → exception")
    void getModuleContent_notFound() {
        when(moduleRepo.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getModuleContent(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("enroll → nouvelle inscription")
    void enroll_new() {
        UUID moduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(enrollmentRepo.findByCompanyIdAndUserIdAndModuleId(any(), eq(userId), eq(moduleId)))
                .thenReturn(Optional.empty());
        when(enrollmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CourseEnrollment result = service.enroll(moduleId, userId);

        assertThat(result.getModuleId()).isEqualTo(moduleId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getStatus()).isEqualTo(CourseEnrollment.Status.NOT_STARTED);
        assertThat(result.getProgressPercent()).isZero();
    }

    @Test
    @DisplayName("enroll → déjà inscrit → retourne existant")
    void enroll_alreadyEnrolled() {
        UUID moduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CourseEnrollment existing = CourseEnrollment.builder()
                .moduleId(moduleId)
                .userId(userId)
                .status(CourseEnrollment.Status.IN_PROGRESS)
                .build();
        when(enrollmentRepo.findByCompanyIdAndUserIdAndModuleId(any(), eq(userId), eq(moduleId)))
                .thenReturn(Optional.of(existing));

        CourseEnrollment result = service.enroll(moduleId, userId);

        assertThat(result).isSameAs(existing);
        verify(enrollmentRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateProgress → met à jour le pourcentage")
    void updateProgress() {
        UUID companyId = TenantContext.get();
        UUID enrollmentId = UUID.randomUUID();
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(enrollmentId)
                .companyId(companyId)
                .status(CourseEnrollment.Status.NOT_STARTED)
                .progressPercent(0)
                .build();
        when(enrollmentRepo.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CourseEnrollment result = service.updateProgress(enrollmentId, 50);

        assertThat(result.getProgressPercent()).isEqualTo(50);
        assertThat(result.getStatus()).isEqualTo(CourseEnrollment.Status.IN_PROGRESS);
    }

    @Test
    @DisplayName("updateProgress → 100% → marque COMPLETED")
    void updateProgress_completed() {
        UUID companyId = TenantContext.get();
        UUID enrollmentId = UUID.randomUUID();
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(enrollmentId)
                .companyId(companyId)
                .status(CourseEnrollment.Status.IN_PROGRESS)
                .progressPercent(50)
                .build();
        when(enrollmentRepo.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CourseEnrollment result = service.updateProgress(enrollmentId, 100);

        assertThat(result.getProgressPercent()).isEqualTo(100);
        assertThat(result.getStatus()).isEqualTo(CourseEnrollment.Status.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("updateProgress → valeur négative → clamp à 0")
    void updateProgress_clampMin() {
        UUID companyId = TenantContext.get();
        UUID enrollmentId = UUID.randomUUID();
        CourseEnrollment enrollment = CourseEnrollment.builder().id(enrollmentId).companyId(companyId).build();
        when(enrollmentRepo.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CourseEnrollment result = service.updateProgress(enrollmentId, -10);

        assertThat(result.getProgressPercent()).isZero();
    }

    @Test
    @DisplayName("updateProgress → valeur > 100 → clamp à 100")
    void updateProgress_clampMax() {
        UUID companyId = TenantContext.get();
        UUID enrollmentId = UUID.randomUUID();
        CourseEnrollment enrollment = CourseEnrollment.builder().id(enrollmentId).companyId(companyId).build();
        when(enrollmentRepo.findById(enrollmentId)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CourseEnrollment result = service.updateProgress(enrollmentId, 200);

        assertThat(result.getProgressPercent()).isEqualTo(100);
    }

    @Test
    @DisplayName("updateProgress → inscription d'une autre entreprise → exception (isolation tenant)")
    void updateProgress_wrongCompany_throws() {
        UUID enrollmentId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(enrollmentId)
                .companyId(otherCompanyId)
                .status(CourseEnrollment.Status.IN_PROGRESS)
                .progressPercent(20)
                .build();
        when(enrollmentRepo.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.updateProgress(enrollmentId, 50))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(enrollmentRepo, never()).save(any());
    }

    @Test
    @DisplayName("submitQuiz → succès")
    void submitQuiz_success() {
        UUID companyId = TenantContext.get();
        UUID moduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Map<String, Object> questions = new LinkedHashMap<>();
        List<Map<String, Object>> qList = new ArrayList<>();
        qList.add(Map.of("id", "q1", "correctAnswer", "A"));
        qList.add(Map.of("id", "q2", "correctAnswer", "B"));
        qList.add(Map.of("id", "q3", "correctAnswer", "C"));
        questions.put("questions", qList);

        TrainingModule module = TrainingModule.builder()
                .id(moduleId)
                .quizData(questions)
                .passingScore(60)
                .build();
        when(moduleRepo.findById(moduleId)).thenReturn(Optional.of(module));

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(UUID.randomUUID())
                .companyId(companyId)
                .moduleId(moduleId)
                .userId(userId)
                .quizAttempts(0)
                .status(CourseEnrollment.Status.IN_PROGRESS)
                .build();
        when(enrollmentRepo.findByCompanyIdAndUserIdAndModuleId(companyId, userId, moduleId))
                .thenReturn(Optional.of(enrollment));
        when(quizAttemptRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(enrollmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, String> answers = Map.of("q1", "A", "q2", "B", "q3", "C");
        Map<String, Object> result = service.submitQuiz(moduleId, userId, answers);

        assertThat(result.get("score")).isEqualTo(100);
        assertThat(result.get("correct")).isEqualTo(3);
        assertThat(result.get("total")).isEqualTo(3);
        assertThat(result.get("passed")).isEqualTo(true);
    }

    @Test
    @DisplayName("submitQuiz → pas de quiz → exception")
    void submitQuiz_noQuiz() {
        UUID moduleId = UUID.randomUUID();
        TrainingModule module = TrainingModule.builder().id(moduleId).quizData(null).build();
        when(moduleRepo.findById(moduleId)).thenReturn(Optional.of(module));

        assertThatThrownBy(() -> service.submitQuiz(moduleId, UUID.randomUUID(), Map.of()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("getCertification → succès")
    void getCertification_success() {
        UUID companyId = TenantContext.get();
        UUID enrollmentId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(enrollmentId)
                .companyId(companyId)
                .moduleId(moduleId)
                .userId(UUID.randomUUID())
                .status(CourseEnrollment.Status.COMPLETED)
                .bestScore(85)
                .build();
        when(enrollmentRepo.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

        TrainingModule module = TrainingModule.builder()
                .id(moduleId)
                .title("Logistics Basics")
                .build();
        when(moduleRepo.findById(moduleId)).thenReturn(Optional.of(module));

        Map<String, Object> cert = service.getCertification(enrollmentId);

        assertThat(cert)
                .containsKey("certificateNumber")
                .containsEntry("moduleTitle", "Logistics Basics")
                .containsEntry("score", 85)
                .containsEntry("status", "VALID");
        assertThat((String) cert.get("certificateNumber")).startsWith("INK-ACADEMY-");
    }

    @Test
    @DisplayName("getCertification → pas COMPLETED → exception")
    void getCertification_notCompleted() {
        UUID enrollmentId = UUID.randomUUID();
        UUID companyId = TenantContext.get();
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .id(enrollmentId)
                .companyId(companyId)
                .status(CourseEnrollment.Status.IN_PROGRESS)
                .build();
        when(enrollmentRepo.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.getCertification(enrollmentId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getDashboard → retourne les stats")
    void getDashboard() {
        UUID companyId = TenantContext.get();

        when(enrollmentRepo.countByCompanyId(companyId)).thenReturn(50L);
        when(enrollmentRepo.countByCompanyIdAndStatus(companyId, CourseEnrollment.Status.COMPLETED)).thenReturn(30L);
        when(enrollmentRepo.countByCompanyIdAndStatus(companyId, CourseEnrollment.Status.IN_PROGRESS)).thenReturn(10L);
        when(enrollmentRepo.countByCompanyIdAndStatus(companyId, CourseEnrollment.Status.FAILED)).thenReturn(5L);
        when(enrollmentRepo.findTopModulesByCompanyId(companyId)).thenReturn(List.of());

        Map<String, Object> dashboard = service.getDashboard();

        assertThat(dashboard)
                .containsEntry("totalEnrolled", 50L)
                .containsEntry("completed", 30L)
                .containsEntry("inProgress", 10L)
                .containsEntry("failed", 5L)
                .containsEntry("passRate", 60.0);
    }

    @Test
    @DisplayName("createModule → sauvegarde et retourne")
    void createModule() {
        TrainingModule module = TrainingModule.builder().title("New Module").build();
        when(moduleRepo.save(module)).thenReturn(module);

        TrainingModule result = service.createModule(module);

        assertThat(result.getTitle()).isEqualTo("New Module");
    }

    @Test
    @DisplayName("updateModule → met à jour et retourne")
    void updateModule() {
        UUID id = UUID.randomUUID();
        TrainingModule existing = TrainingModule.builder().id(id).title("Old Title").build();
        TrainingModule updated = TrainingModule.builder()
                .title("New Title")
                .description("New desc")
                .category(TrainingModule.Category.COMPLIANCE)
                .difficulty(TrainingModule.Difficulty.ADVANCED)
                .durationMinutes(60)
                .passingScore(70)
                .isPublished(true)
                .build();
        when(moduleRepo.findById(id)).thenReturn(Optional.of(existing));
        when(moduleRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        TrainingModule result = service.updateModule(id, updated);

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getCategory()).isEqualTo(TrainingModule.Category.COMPLIANCE);
    }
}
