package com.incokalk.dto.shared;

import com.incokalk.model.EmailIntakeLog;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmailIntakeLogResponse(
        UUID id,
        UUID emailIntakeId,
        EmailIntakeLog.LogStatus status,
        String message,
        Integer processedCount,
        Integer errorCount,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
    public static EmailIntakeLogResponse from(EmailIntakeLog l) {
        return new EmailIntakeLogResponse(
                l.getId(), l.getMailbox().getId(), l.getStatus(), l.getMessage(),
                l.getProcessedCount(), l.getErrorCount(), l.getStartedAt(), l.getCompletedAt()
        );
    }
}
