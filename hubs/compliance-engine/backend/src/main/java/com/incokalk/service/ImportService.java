package com.incokalk.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class ImportService {

    // importCarriersCsv a ete retiree avec le domaine Transport (CarrierRepository).

    // ── CSV Preview (first 5 rows) ───────────────────────────────────────

    public Map<String, Object> previewCsv(MultipartFile file) throws Exception {
        List<String[]> headers = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        int count = 0;

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                .withSkipLines(0)
                .build()) {

            String[] line;
            while ((line = reader.readNext()) != null && count < 6) {
                if (count == 0) {
                    headers.add(line);
                } else {
                    rows.add(line);
                }
                count++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headers", headers.isEmpty() ? new String[0] : headers.get(0));
        result.put("preview", rows);
        result.put("totalRows", count - 1);
        return result;
    }
}
