package com.incokalk.service;

import com.incokalk.dto.shared.EmailMailboxRequest;
import com.incokalk.dto.shared.EmailMailboxResponse;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.EmailMailbox;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EmailIntakeLogRepository;
import com.incokalk.repository.EmailMailboxRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("EmailMailboxService — Tests unitaires")
class EmailMailboxServiceTest {

    EmailMailboxService service;
    EmailMailboxRepository mailboxRepo;
    EmailIntakeLogRepository logRepo;
    CompanyRepository companyRepo;
    CredentialEncryptionService encryptionService;
    UUID companyId;

    @BeforeEach
    void setUp() {
        mailboxRepo = mock(EmailMailboxRepository.class);
        logRepo = mock(EmailIntakeLogRepository.class);
        companyRepo = mock(CompanyRepository.class);
        encryptionService = mock(CredentialEncryptionService.class);
        service = new EmailMailboxService(mailboxRepo, logRepo, companyRepo, encryptionService);
        companyId = UUID.randomUUID();
        TenantContext.set(companyId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private EmailMailboxRequest fullRequest() {
        return new EmailMailboxRequest("intake@test.com", "imap.test.com", 993, "intake@test.com",
                "secret", "INBOX", EmailMailbox.Protocol.IMAP, true, true, "SHIPMENT_ORDER", false, true);
    }

    @Test
    @DisplayName("list → retourne les boîtes de la company courante")
    void list_returnsMailboxesForCurrentCompany() {
        EmailMailbox mb = EmailMailbox.builder().id(UUID.randomUUID()).email("a@test.com").build();
        when(mailboxRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(mb));

        List<EmailMailboxResponse> result = service.list();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("logs → retourne les logs de la company courante")
    void logs_returnsLogsForCurrentCompany() {
        when(logRepo.findByMailbox_Company_IdOrderByStartedAtDesc(companyId)).thenReturn(List.of());

        List<?> result = service.logs();

        assertThat(result).isEmpty();
        verify(logRepo).findByMailbox_Company_IdOrderByStartedAtDesc(companyId);
    }

    @Test
    @DisplayName("create → email manquant → IllegalArgumentException")
    void create_missingEmail_throws() {
        EmailMailboxRequest req = new EmailMailboxRequest(null, "imap.test.com", 993, "user",
                "secret", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obligatoires");
        verify(mailboxRepo, never()).save(any());
    }

    @Test
    @DisplayName("create → hôte manquant → IllegalArgumentException")
    void create_missingHost_throws() {
        EmailMailboxRequest req = new EmailMailboxRequest("a@test.com", "  ", 993, "user",
                "secret", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create → utilisateur manquant → IllegalArgumentException")
    void create_missingUsername_throws() {
        EmailMailboxRequest req = new EmailMailboxRequest("a@test.com", "imap.test.com", 993, "",
                "secret", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create → mot de passe manquant → IllegalArgumentException")
    void create_missingPassword_throws() {
        EmailMailboxRequest req = new EmailMailboxRequest("a@test.com", "imap.test.com", 993, "user",
                "   ", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mot de passe");
    }

    @Test
    @DisplayName("create → champs optionnels absents → valeurs par défaut appliquées")
    void create_withoutOptionalFields_appliesDefaults() {
        Company company = Company.builder().id(companyId).build();
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(encryptionService.encrypt("secret")).thenReturn("enc-secret");
        when(mailboxRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        EmailMailboxRequest req = new EmailMailboxRequest("a@test.com", "imap.test.com", null, "user",
                "secret", null, null, null, null, null, null, null);

        EmailMailboxResponse result = service.create(req);

        ArgumentCaptor<EmailMailbox> captor = ArgumentCaptor.forClass(EmailMailbox.class);
        verify(mailboxRepo).save(captor.capture());
        EmailMailbox saved = captor.getValue();
        assertThat(saved.getImapPort()).isEqualTo(993);
        assertThat(saved.getFolder()).isEqualTo("INBOX");
        assertThat(saved.getProtocol()).isEqualTo(EmailMailbox.Protocol.IMAP);
        assertThat(saved.getSslEnabled()).isTrue();
        assertThat(saved.getAutoImport()).isFalse();
        assertThat(saved.getTargetDocumentType()).isEqualTo("SHIPMENT_ORDER");
        assertThat(saved.getDeleteAfterImport()).isFalse();
        assertThat(saved.getIsActive()).isTrue();
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("create → champs optionnels fournis explicitement → valeurs conservées")
    void create_withExplicitOptionalFields_keepsValues() {
        Company company = Company.builder().id(companyId).build();
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(encryptionService.encrypt("secret")).thenReturn("enc-secret");
        when(mailboxRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        EmailMailboxRequest req = new EmailMailboxRequest("a@test.com", "pop.test.com", 995, "user",
                "secret", "Archive", EmailMailbox.Protocol.POP3, false, true, "INVOICE", true, false);

        service.create(req);

        ArgumentCaptor<EmailMailbox> captor = ArgumentCaptor.forClass(EmailMailbox.class);
        verify(mailboxRepo).save(captor.capture());
        EmailMailbox saved = captor.getValue();
        assertThat(saved.getImapPort()).isEqualTo(995);
        assertThat(saved.getFolder()).isEqualTo("Archive");
        assertThat(saved.getProtocol()).isEqualTo(EmailMailbox.Protocol.POP3);
        assertThat(saved.getSslEnabled()).isFalse();
        assertThat(saved.getAutoImport()).isTrue();
        assertThat(saved.getTargetDocumentType()).isEqualTo("INVOICE");
        assertThat(saved.getDeleteAfterImport()).isTrue();
        assertThat(saved.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("update → boîte introuvable → ResourceNotFoundException")
    void update_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(mailboxRepo.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, fullRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update → met à jour uniquement les champs fournis")
    void update_partialFields_onlyUpdatesProvided() {
        UUID id = UUID.randomUUID();
        EmailMailbox existing = EmailMailbox.builder()
                .id(id).email("old@test.com").imapHost("old-host").username("old-user")
                .folder("INBOX").isActive(true).build();
        when(mailboxRepo.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(existing));
        when(mailboxRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        EmailMailboxRequest req = new EmailMailboxRequest("new@test.com", null, null, null,
                null, null, null, null, null, null, null, null);

        EmailMailboxResponse result = service.update(id, req);

        assertThat(existing.getEmail()).isEqualTo("new@test.com");
        assertThat(existing.getImapHost()).isEqualTo("old-host");
        assertThat(existing.getUsername()).isEqualTo("old-user");
        verify(encryptionService, never()).encrypt(any());
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("update → nouveau mot de passe fourni → re-chiffré")
    void update_withNewPassword_reEncrypts() {
        UUID id = UUID.randomUUID();
        EmailMailbox existing = EmailMailbox.builder().id(id).build();
        when(mailboxRepo.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(existing));
        when(encryptionService.encrypt("newpass")).thenReturn("enc-newpass");
        when(mailboxRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        EmailMailboxRequest req = new EmailMailboxRequest(null, null, null, null,
                "newpass", null, null, null, null, null, null, null);

        service.update(id, req);

        assertThat(existing.getEncryptedPassword()).isEqualTo("enc-newpass");
    }

    @Test
    @DisplayName("delete → boîte introuvable → ResourceNotFoundException")
    void delete_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(mailboxRepo.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(mailboxRepo, never()).delete(any());
    }

    @Test
    @DisplayName("delete → boîte trouvée → supprimée")
    void delete_found_deletes() {
        UUID id = UUID.randomUUID();
        EmailMailbox existing = EmailMailbox.builder().id(id).build();
        when(mailboxRepo.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(existing));

        service.delete(id);

        verify(mailboxRepo).delete(existing);
    }

    @Test
    @DisplayName("testConnection → boîte introuvable → ResourceNotFoundException")
    void testConnection_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(mailboxRepo.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.testConnection(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("testConnection → échec de connexion → IllegalStateException, dernière erreur enregistrée")
    void testConnection_connectionFails_recordsErrorAndThrows() {
        UUID id = UUID.randomUUID();
        // Port 1 is a reserved, essentially never-listening port: connection is refused
        // immediately rather than hanging until a network timeout.
        EmailMailbox existing = EmailMailbox.builder()
                .id(id).email("a@test.com").imapHost("localhost").imapPort(1)
                .protocol(EmailMailbox.Protocol.IMAP).sslEnabled(false)
                .username("user").encryptedPassword("enc").build();
        when(mailboxRepo.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(existing));
        when(encryptionService.decrypt("enc")).thenReturn("plain");
        when(mailboxRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.testConnection(id)).isInstanceOf(IllegalStateException.class);

        assertThat(existing.getLastError()).isNotNull();
        assertThat(existing.getLastCheckAt()).isNotNull();
        verify(mailboxRepo).save(existing);
    }
}
