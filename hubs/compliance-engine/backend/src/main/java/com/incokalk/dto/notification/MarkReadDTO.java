package com.incokalk.dto.notification;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkReadDTO {

    @NotNull(message = "La liste des IDs est obligatoire")
    private List<UUID> notificationIds;
}
