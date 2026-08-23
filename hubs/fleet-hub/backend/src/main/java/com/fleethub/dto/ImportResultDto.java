package com.fleethub.dto;

import java.util.List;

public record ImportResultDto(
        String fileType,
        int rowsRead,
        int rowsImported,
        int rowsSkipped,
        List<String> errors
) {
}
