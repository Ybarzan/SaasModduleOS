package com.fleethub.service;

import com.fleethub.model.Company;
import com.fleethub.model.Company.CompanyStatus;
import com.fleethub.model.AppUser;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrialExpiryReminderTaskTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private com.fleethub.service.email.EmailNotifier emailNotifier;

    private TrialExpiryReminderTask task;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 10, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        task = new TrialExpiryReminderTask(companyRepository, userRepository, emailNotifier, FIXED_CLOCK);
    }

    @Test
    void remind_sendsJ1WhenOneDayRemaining() {
        Company c = Company.builder()
                .id(1L)
                .name("Société")
                .status(CompanyStatus.TRIAL)
                .trialEndsAt(NOW.plusDays(1))
                .build();

        AppUser admin = new AppUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setEmail("admin@example.fr");
        admin.setRole("ADMIN");
        admin.setEnabled(true);

        when(companyRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(c));
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of(admin));

        task.remind();

        verify(emailNotifier, times(1)).trialExpiring(any(), any(), any());
    }

    @Test
    void remind_doesNotSendOnExpiryDay() {
        Company c = Company.builder()
                .id(1L)
                .name("Société")
                .status(CompanyStatus.TRIAL)
                .trialEndsAt(NOW)
                .build();

        when(companyRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(c));

        task.remind();

        verify(emailNotifier, never()).trialExpiring(any(), any(), any());
    }

    @Test
    void remind_doesNotSendWhenDaysLeftExceeds7() {
        Company c = Company.builder()
                .id(1L)
                .name("Société")
                .status(CompanyStatus.TRIAL)
                .trialEndsAt(NOW.plusDays(10))
                .build();

        when(companyRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(c));

        task.remind();

        verify(emailNotifier, never()).trialExpiring(any(), any(), any());
    }

    @Test
    void remind_doesNotSendWhenTrialAlreadyExpired() {
        Company c = Company.builder()
                .id(1L)
                .name("Société")
                .status(CompanyStatus.TRIAL)
                .trialEndsAt(NOW.minusDays(1))
                .build();

        when(companyRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(c));

        task.remind();

        verify(emailNotifier, never()).trialExpiring(any(), any(), any());
    }

    @Test
    void remind_notifiesOnlyEnabledAdmins() {
        Company c = Company.builder()
                .id(1L)
                .name("Société")
                .status(CompanyStatus.TRIAL)
                .trialEndsAt(NOW.plusDays(1))
                .build();

        AppUser enabledAdmin = new AppUser();
        enabledAdmin.setId(1L);
        enabledAdmin.setUsername("admin");
        enabledAdmin.setEmail("admin@example.fr");
        enabledAdmin.setRole("ADMIN");
        enabledAdmin.setEnabled(true);

        AppUser disabledAdmin = new AppUser();
        disabledAdmin.setId(2L);
        disabledAdmin.setUsername("inactive");
        disabledAdmin.setEmail("inactive@example.fr");
        disabledAdmin.setRole("ADMIN");
        disabledAdmin.setEnabled(false);

        when(companyRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(c));
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of(enabledAdmin, disabledAdmin));

        task.remind();

        verify(emailNotifier, times(1)).trialExpiring(eq("admin@example.fr"), eq("Société"), any());
        verify(emailNotifier, never()).trialExpiring(eq("inactive@example.fr"), anyString(), any());
    }

    @Test
    void remind_sendsJ7WhenOneWeekRemaining() {
        Company c = Company.builder()
                .id(1L)
                .name("Société")
                .status(CompanyStatus.TRIAL)
                .trialEndsAt(NOW.plusDays(7))
                .build();

        AppUser admin = new AppUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setEmail("admin@example.fr");
        admin.setRole("ADMIN");
        admin.setEnabled(true);

        when(companyRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(c));
        when(userRepository.findByCompanyId(1L)).thenReturn(List.of(admin));

        task.remind();

        verify(emailNotifier, times(1)).trialExpiring(any(), any(), any());
    }
}
