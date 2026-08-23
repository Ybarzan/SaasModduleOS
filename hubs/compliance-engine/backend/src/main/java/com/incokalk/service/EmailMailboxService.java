package com.incokalk.service;

import com.incokalk.dto.shared.EmailIntakeLogResponse;
import com.incokalk.dto.shared.EmailMailboxRequest;
import com.incokalk.dto.shared.EmailMailboxResponse;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.EmailMailbox;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EmailIntakeLogRepository;
import com.incokalk.repository.EmailMailboxRepository;
import com.incokalk.tenant.TenantContext;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailMailboxService {

    private final EmailMailboxRepository mailboxRepo;
    private final EmailIntakeLogRepository logRepo;
    private final CompanyRepository companyRepo;
    private final CredentialEncryptionService encryptionService;

    @Transactional(readOnly = true)
    public List<EmailMailboxResponse> list() {
        UUID companyId = TenantContext.get();
        return mailboxRepo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(EmailMailboxResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmailIntakeLogResponse> logs() {
        UUID companyId = TenantContext.get();
        return logRepo.findByMailbox_Company_IdOrderByStartedAtDesc(companyId).stream()
                .map(EmailIntakeLogResponse::from)
                .toList();
    }

    @Transactional
    public EmailMailboxResponse create(EmailMailboxRequest req) {
        UUID companyId = TenantContext.get();
        if (isBlank(req.email()) || isBlank(req.imapHost()) || isBlank(req.username())) {
            throw new IllegalArgumentException("Email, hôte et utilisateur sont obligatoires");
        }
        if (isBlank(req.password())) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire pour ajouter une boîte email");
        }

        EmailMailbox mailbox = EmailMailbox.builder()
                .company(companyRepo.getReferenceById(companyId))
                .email(req.email())
                .imapHost(req.imapHost())
                .imapPort(req.imapPort() != null ? req.imapPort() : 993)
                .username(req.username())
                .encryptedPassword(encryptionService.encrypt(req.password()))
                .folder(req.folder() != null ? req.folder() : "INBOX")
                .protocol(req.protocol() != null ? req.protocol() : EmailMailbox.Protocol.IMAP)
                .sslEnabled(req.sslEnabled() != null ? req.sslEnabled() : true)
                .autoImport(req.autoImport() != null ? req.autoImport() : false)
                .targetDocumentType(req.targetDocumentType() != null ? req.targetDocumentType() : "SHIPMENT_ORDER")
                .deleteAfterImport(req.deleteAfterImport() != null ? req.deleteAfterImport() : false)
                .isActive(req.isActive() != null ? req.isActive() : true)
                .build();

        return EmailMailboxResponse.from(mailboxRepo.save(mailbox));
    }

    @Transactional
    public EmailMailboxResponse update(UUID id, EmailMailboxRequest req) {
        UUID companyId = TenantContext.get();
        EmailMailbox mailbox = mailboxRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Boîte email non trouvée"));

        if (req.email() != null) mailbox.setEmail(req.email());
        if (req.imapHost() != null) mailbox.setImapHost(req.imapHost());
        if (req.imapPort() != null) mailbox.setImapPort(req.imapPort());
        if (req.username() != null) mailbox.setUsername(req.username());
        if (!isBlank(req.password())) mailbox.setEncryptedPassword(encryptionService.encrypt(req.password()));
        if (req.folder() != null) mailbox.setFolder(req.folder());
        if (req.protocol() != null) mailbox.setProtocol(req.protocol());
        if (req.sslEnabled() != null) mailbox.setSslEnabled(req.sslEnabled());
        if (req.autoImport() != null) mailbox.setAutoImport(req.autoImport());
        if (req.targetDocumentType() != null) mailbox.setTargetDocumentType(req.targetDocumentType());
        if (req.deleteAfterImport() != null) mailbox.setDeleteAfterImport(req.deleteAfterImport());
        if (req.isActive() != null) mailbox.setIsActive(req.isActive());

        return EmailMailboxResponse.from(mailboxRepo.save(mailbox));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        EmailMailbox mailbox = mailboxRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Boîte email non trouvée"));
        mailboxRepo.delete(mailbox);
    }

    @Transactional
    public EmailMailboxResponse testConnection(UUID id) {
        UUID companyId = TenantContext.get();
        EmailMailbox mailbox = mailboxRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Boîte email non trouvée"));

        String protocol = mailbox.getProtocol() == EmailMailbox.Protocol.POP3 ? "pop3s" : "imaps";
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", protocol);
            props.put("mail." + protocol + ".host", mailbox.getImapHost());
            props.put("mail." + protocol + ".port", mailbox.getImapPort());
            props.put("mail." + protocol + ".ssl.enable", String.valueOf(mailbox.getSslEnabled()));

            Session session = Session.getInstance(props);
            Store store = session.getStore(protocol);
            store.connect(mailbox.getImapHost(), mailbox.getUsername(), encryptionService.decrypt(mailbox.getEncryptedPassword()));

            if (mailbox.getProtocol() == EmailMailbox.Protocol.IMAP) {
                Folder folder = store.getFolder(mailbox.getFolder());
                folder.open(Folder.READ_ONLY);
                folder.close(false);
            }
            store.close();

            mailbox.setLastCheckAt(LocalDateTime.now());
            mailbox.setLastError(null);
        } catch (Exception e) {
            log.warn("[EmailMailbox] Échec du test de connexion pour {}: {}", mailbox.getEmail(), e.getMessage());
            mailbox.setLastCheckAt(LocalDateTime.now());
            mailbox.setLastError(e.getMessage() != null ? e.getMessage() : "Erreur de connexion inconnue");
            mailboxRepo.save(mailbox);
            throw new IllegalStateException("Échec de la connexion : " + mailbox.getLastError());
        }

        return EmailMailboxResponse.from(mailboxRepo.save(mailbox));
    }
}
