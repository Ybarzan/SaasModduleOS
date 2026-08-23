package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_mailboxes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailMailbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "imap_host", nullable = false, length = 255)
    private String imapHost;

    @Column(name = "imap_port", nullable = false)
    @Builder.Default
    private Integer imapPort = 993;

    @Column(nullable = false, length = 255)
    private String username;

    @Column(name = "encrypted_password", nullable = false, length = 1000)
    private String encryptedPassword;

    @Column(nullable = false, length = 100)
    @Builder.Default
    private String folder = "INBOX";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Protocol protocol = Protocol.IMAP;

    @Column(name = "ssl_enabled", nullable = false)
    @Builder.Default
    private Boolean sslEnabled = true;

    @Column(name = "auto_import", nullable = false)
    @Builder.Default
    private Boolean autoImport = false;

    @Column(name = "target_document_type", nullable = false, length = 30)
    @Builder.Default
    private String targetDocumentType = "SHIPMENT_ORDER";

    @Column(name = "delete_after_import", nullable = false)
    @Builder.Default
    private Boolean deleteAfterImport = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_check_at")
    private LocalDateTime lastCheckAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Protocol { IMAP, POP3 }
}
