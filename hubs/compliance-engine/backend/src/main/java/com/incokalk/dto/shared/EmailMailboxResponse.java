package com.incokalk.dto.shared;

import com.incokalk.model.EmailMailbox;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmailMailboxResponse(
        UUID id,
        String email,
        String imapHost,
        Integer imapPort,
        String username,
        EmailMailbox.Protocol protocol,
        Boolean sslEnabled,
        String folder,
        Boolean autoImport,
        Boolean deleteAfterImport,
        String targetDocumentType,
        Boolean isActive,
        LocalDateTime lastCheckAt,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EmailMailboxResponse from(EmailMailbox m) {
        return new EmailMailboxResponse(
                m.getId(), m.getEmail(), m.getImapHost(), m.getImapPort(), m.getUsername(),
                m.getProtocol(), m.getSslEnabled(), m.getFolder(), m.getAutoImport(),
                m.getDeleteAfterImport(), m.getTargetDocumentType(), m.getIsActive(),
                m.getLastCheckAt(), m.getLastError(), m.getCreatedAt(), m.getUpdatedAt()
        );
    }
}
