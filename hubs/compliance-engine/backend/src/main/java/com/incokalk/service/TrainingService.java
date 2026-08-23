package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.CourseEnrollment;
import com.incokalk.model.CourseEnrollment.Status;
import com.incokalk.model.QuizAttempt;
import com.incokalk.model.TrainingModule;
import com.incokalk.repository.CourseEnrollmentRepository;
import com.incokalk.repository.QuizAttemptRepository;
import com.incokalk.repository.TrainingModuleRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingModuleRepository moduleRepo;
    private final CourseEnrollmentRepository enrollmentRepo;
    private final QuizAttemptRepository quizAttemptRepo;

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public List<TrainingModule> getCatalog() {
        return moduleRepo.findByIsPublishedTrueOrderByCreatedAtDesc();
    }

    public Map<String, Object> getModuleContent(UUID moduleId) {
        UUID companyId = TenantContext.get();
        TrainingModule module = moduleRepo.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module non trouvé"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", module);

        Optional<CourseEnrollment> enrollment = enrollmentRepo
                .findByCompanyIdAndUserIdAndModuleId(companyId, getCurrentUserId(), moduleId);
        enrollment.ifPresent(e -> result.put("enrollment", e));

        return result;
    }

    @Transactional
    public CourseEnrollment enroll(UUID moduleId, UUID userId) {
        UUID companyId = TenantContext.get();

        Optional<CourseEnrollment> existing = enrollmentRepo
                .findByCompanyIdAndUserIdAndModuleId(companyId, userId, moduleId);
        if (existing.isPresent()) {
            return existing.get();
        }

        CourseEnrollment enrollment = CourseEnrollment.builder()
                .moduleId(moduleId)
                .companyId(companyId)
                .userId(userId)
                .status(Status.NOT_STARTED)
                .progressPercent(0)
                .quizAttempts(0)
                .build();

        return enrollmentRepo.save(enrollment);
    }

    @Transactional
    public CourseEnrollment updateProgress(UUID enrollmentId, int percent) {
        UUID companyId = TenantContext.get();
        CourseEnrollment enrollment = enrollmentRepo.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription non trouvée"));

        if (!companyId.equals(enrollment.getCompanyId())) {
            throw new ResourceNotFoundException("Inscription non trouvée");
        }

        enrollment.setProgressPercent(Math.min(100, Math.max(0, percent)));

        if (percent > 0 && enrollment.getStatus() == Status.NOT_STARTED) {
            enrollment.setStatus(Status.IN_PROGRESS);
            enrollment.setStartedAt(LocalDateTime.now());
        }

        if (percent >= 100 && enrollment.getStatus() != Status.COMPLETED) {
            enrollment.setStatus(Status.COMPLETED);
            enrollment.setCompletedAt(LocalDateTime.now());
        }

        return enrollmentRepo.save(enrollment);
    }

    @Transactional
    public Map<String, Object> submitQuiz(UUID moduleId, UUID userId, Map<String, String> answers) {
        UUID companyId = TenantContext.get();

        TrainingModule module = moduleRepo.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module non trouvé"));

        CourseEnrollment enrollment = enrollmentRepo
                .findByCompanyIdAndUserIdAndModuleId(companyId, userId, moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription non trouvée"));

        Map<String, Object> quizData = module.getQuizData();
        if (quizData == null || quizData.isEmpty()) {
            throw new IllegalStateException("Ce module n'a pas de quiz");
        }

        int correct = 0;
        int total = 0;

        Object questionsObj = quizData.get("questions");
        if (questionsObj instanceof List<?> questions) {
            total = questions.size();
            for (Object qObj : questions) {
                if (qObj instanceof Map<?, ?> question) {
                    String qId = String.valueOf(question.get("id"));
                    Object correctAnswer = question.get("correctAnswer");
                    String userAnswer = answers.get(qId);
                    if (correctAnswer != null && correctAnswer.toString().equalsIgnoreCase(userAnswer != null ? userAnswer : "")) {
                        correct++;
                    }
                }
            }
        }

        int score = total > 0 ? (correct * 100) / total : 0;
        boolean passed = score >= module.getPassingScore();

        int attemptNumber = enrollment.getQuizAttempts() + 1;

        QuizAttempt attempt = QuizAttempt.builder()
                .enrollmentId(enrollment.getId())
                .moduleId(moduleId)
                .companyId(companyId)
                .answers(new LinkedHashMap<>(answers))
                .score(score)
                .passed(passed)
                .attemptNumber(attemptNumber)
                .startedAt(LocalDateTime.now())
                .submittedAt(LocalDateTime.now())
                .build();
        quizAttemptRepo.save(attempt);

        enrollment.setQuizAttempts(attemptNumber);
        if (score > (enrollment.getBestScore() != null ? enrollment.getBestScore() : 0)) {
            enrollment.setBestScore(score);
        }

        if (passed) {
            enrollment.setStatus(Status.COMPLETED);
            enrollment.setProgressPercent(100);
            enrollment.setCompletedAt(LocalDateTime.now());
        }

        enrollmentRepo.save(enrollment);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attempt", attempt);
        result.put("score", score);
        result.put("total", total);
        result.put("correct", correct);
        result.put("passed", passed);
        result.put("passingScore", module.getPassingScore());
        return result;
    }

    public Map<String, Object> getCertification(UUID enrollmentId) {
        UUID companyId = TenantContext.get();
        CourseEnrollment enrollment = enrollmentRepo.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription non trouvée"));

        if (!companyId.equals(enrollment.getCompanyId())) {
            throw new ResourceNotFoundException("Inscription non trouvée");
        }

        if (enrollment.getStatus() != Status.COMPLETED || enrollment.getBestScore() == null) {
            throw new IllegalStateException("Module non terminé ou aucun score disponible");
        }

        TrainingModule module = moduleRepo.findById(enrollment.getModuleId())
                .orElseThrow(() -> new ResourceNotFoundException("Module non trouvé"));

        Map<String, Object> cert = new LinkedHashMap<>();
        cert.put("certificateNumber", generateCertificateNumber());
        cert.put("userId", enrollment.getUserId());
        cert.put("moduleTitle", module.getTitle());
        cert.put("moduleId", module.getId());
        cert.put("score", enrollment.getBestScore());
        cert.put("date", LocalDateTime.now().toString());
        cert.put("status", "VALID");

        return cert;
    }

    public Map<String, Object> getDashboard() {
        UUID companyId = TenantContext.get();

        long totalEnrolled = enrollmentRepo.countByCompanyId(companyId);
        long completed = enrollmentRepo.countByCompanyIdAndStatus(companyId, Status.COMPLETED);
        long inProgress = enrollmentRepo.countByCompanyIdAndStatus(companyId, Status.IN_PROGRESS);
        long failed = enrollmentRepo.countByCompanyIdAndStatus(companyId, Status.FAILED);

        double passRate = totalEnrolled > 0
                ? Math.round((double) completed / totalEnrolled * 100.0 * 100.0) / 100.0
                : 0.0;

        List<Object[]> topRaw = enrollmentRepo.findTopModulesByCompanyId(companyId);
        List<Map<String, Object>> topModules = new ArrayList<>();
        for (int i = 0; i < Math.min(topRaw.size(), 5); i++) {
            Object[] row = topRaw.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("moduleId", row[0]);
            m.put("count", row[1]);
            moduleRepo.findById((UUID) row[0]).ifPresent(mod -> {
                m.put("title", mod.getTitle());
                m.put("category", mod.getCategory());
            });
            topModules.add(m);
        }

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("totalEnrolled", totalEnrolled);
        dashboard.put("completed", completed);
        dashboard.put("inProgress", inProgress);
        dashboard.put("failed", failed);
        dashboard.put("passRate", passRate);
        dashboard.put("topModules", topModules);

        return dashboard;
    }

    private String generateCertificateNumber() {
        int year = Year.now().getValue();
        String random = RANDOM.ints(8, 0, ALPHANUM.length())
                .mapToObj(ALPHANUM::charAt)
                .map(String::valueOf)
                .collect(Collectors.joining());
        return "INK-ACADEMY-" + year + "-" + random;
    }

    private UUID getCurrentUserId() {
        return UUID.randomUUID();
    }

    public TrainingModule createModule(TrainingModule module) {
        return moduleRepo.save(module);
    }

    public TrainingModule updateModule(UUID id, TrainingModule updated) {
        TrainingModule existing = moduleRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module non trouvé"));

        existing.setTitle(updated.getTitle());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setDifficulty(updated.getDifficulty());
        existing.setDurationMinutes(updated.getDurationMinutes());
        existing.setContentHtml(updated.getContentHtml());
        existing.setVideoUrl(updated.getVideoUrl());
        existing.setQuizData(updated.getQuizData());
        existing.setPassingScore(updated.getPassingScore());
        existing.setIsPublished(updated.getIsPublished());
        existing.setImageUrl(updated.getImageUrl());

        return moduleRepo.save(existing);
    }
}
