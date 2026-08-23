package com.incokalk.dto.shared;

import com.incokalk.model.EmailMailbox;

public record EmailMailboxRequest(
        String email,
        String imapHost,
        Integer imapPort,
        String username,
        String password,
        String folder,
        EmailMailbox.Protocol protocol,
        Boolean sslEnabled,
        Boolean autoImport,
        String targetDocumentType,
        Boolean deleteAfterImport,
        Boolean isActive
) {}
