package com.incokalk.service;

import com.incokalk.model.Carrier;
import com.incokalk.model.Company;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final CarrierRepository carrierRepo;
    private final ShipmentOrderRepository shipmentRepo;
    private final CompanyRepository companyRepo;

    // ── Carriers CSV Import ───────────────────────────────────────────────

    public Map<String, Object> importCarriersCsv(MultipartFile file, UUID companyId) throws Exception {
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                .withSkipLines(1)
                .build()) {

            String[] line;
            int lineNum = 1;
            while ((line = reader.readNext()) != null) {
                lineNum++;
                try {
                    if (line.length < 2 || line[0].isBlank() || line[1].isBlank()) {
                        errors.add("Ligne " + lineNum + ": nom ou code manquant");
                        skipped++;
                        continue;
                    }

                    String name = line[0].trim();
                    String code = line[1].trim();

                    if (carrierRepo.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
                        skipped++;
                        continue;
                    }

                    Carrier carrier = Carrier.builder()
                        .name(name)
                        .code(code)
                        .transportModes(line.length > 2 ? line[2].trim() : "SEA")
                        .contactName(line.length > 3 ? line[3].trim() : null)
                        .contactEmail(line.length > 4 ? line[4].trim() : null)
                        .contactPhone(line.length > 5 ? line[5].trim() : null)
                        .country(line.length > 6 ? line[6].trim() : null)
                        .build();

                    Company company = companyRepo.getReferenceById(companyId);
                    carrier.setCompany(company);
                    carrierRepo.save(carrier);
                    imported++;
                } catch (Exception e) {
                    errors.add("Ligne " + lineNum + ": " + e.getMessage());
                    skipped++;
                }
            }
        }

        log.info("Import CSV carriers: {} importés, {} ignorés, {} erreurs pour company {}", imported, skipped, errors.size(), companyId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

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
