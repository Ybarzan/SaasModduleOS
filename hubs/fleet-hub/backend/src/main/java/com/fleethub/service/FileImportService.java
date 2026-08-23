package com.fleethub.service;

import com.fleethub.dto.ImportResultDto;
import com.fleethub.integration.IntegrationSyncService;
import com.fleethub.integration.parser.DddFileParser;
import com.fleethub.integration.parser.FuelFileParser;
import com.fleethub.integration.parser.TachoFileParser;
import com.fleethub.model.ImportHistory;
import com.fleethub.repository.ImportHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileImportService {

    private static final Logger log = LoggerFactory.getLogger(FileImportService.class);

    private final IntegrationSyncService syncService;
    private final ImportHistoryRepository importHistoryRepository;

    public ImportResultDto importTachograph(MultipartFile file, Long companyId) throws IOException {
        String filename = file.getOriginalFilename();
        boolean isDdd = filename != null && filename.toLowerCase().endsWith(".ddd");

        int saved;
        int rowsRead;
        List<String> errors;
        int skipped;

        if (isDdd) {
            DddFileParser dddParser = new DddFileParser();
            DddFileParser.ParseResult result = dddParser.parse(file.getInputStream());
            saved = syncService.ingestTachographDays(result.rows(), companyId);
            rowsRead = result.rows().size();
            errors = new ArrayList<>(result.errors());
            skipped = rowsRead - saved;
            if (skipped > 0 && errors.isEmpty()) {
                errors.add(skipped + " ligne(s) ignorée(s) (chauffeur non trouvé)");
            }
            log.info("Import DDD tenant {}: {} jours lus, {} importés, {} ignorés, {} erreurs",
                    companyId, rowsRead, saved, skipped, result.errors().size());
        } else {
            TachoFileParser parser = new TachoFileParser();
            TachoFileParser.ParseResult result = parser.parse(file.getInputStream());
            saved = syncService.ingestTachographDays(result.rows(), companyId);
            rowsRead = result.rows().size();
            errors = new ArrayList<>(result.errors());
            skipped = rowsRead - saved;
            if (skipped > 0 && errors.isEmpty()) {
                errors.add(skipped + " ligne(s) ignorée(s) (chauffeur non trouvé)");
            }
            log.info("Import tachygraphe CSV tenant {}: {} lues, {} importées, {} ignorées, {} erreurs",
                    companyId, rowsRead, saved, skipped, result.errors().size());
        }

        saveHistory(companyId, "TACHOGRAPH", file.getOriginalFilename(),
                rowsRead, saved, skipped, errors.size() - skipped);

        return new ImportResultDto(
                "TACHOGRAPH",
                rowsRead,
                saved,
                skipped,
                errors);
    }

    public ImportResultDto importFuel(MultipartFile file, Long companyId) throws IOException {
        FuelFileParser parser = new FuelFileParser();
        FuelFileParser.ParseResult result = parser.parse(file.getInputStream());

        int saved = syncService.ingestFuelTransactions(result.rows(), companyId);

        List<String> errors = new ArrayList<>(result.errors());
        int skipped = result.rows().size() - saved;
        if (skipped > 0 && errors.isEmpty()) {
            errors.add(skipped + " ligne(s) ignorée(s) (camion non trouvé ou doublon)");
        }

        log.info("Import carburant tenant {}: {} lues, {} importées, {} ignorées, {} erreurs",
                companyId, result.rows().size(), saved, skipped, result.errors().size());

        saveHistory(companyId, "FUEL", file.getOriginalFilename(),
                result.rows().size(), saved, skipped, result.errors().size());

        return new ImportResultDto(
                "FUEL",
                result.rows().size(),
                saved,
                skipped,
                errors);
    }

    public List<ImportHistory> getHistory(Long companyId) {
        return importHistoryRepository.findByCompanyIdOrderByImportedAtDesc(companyId);
    }

    private void saveHistory(Long companyId, String fileType, String fileName,
                             int rowsRead, int rowsImported, int rowsSkipped, int errorCount) {
        try {
            ImportHistory entry = ImportHistory.builder()
                    .companyId(companyId)
                    .fileType(fileType)
                    .fileName(fileName)
                    .rowsRead(rowsRead)
                    .rowsImported(rowsImported)
                    .rowsSkipped(rowsSkipped)
                    .errorCount(errorCount)
                    .importedAt(LocalDateTime.now())
                    .build();
            importHistoryRepository.save(entry);
        } catch (Exception e) {
            log.warn("Erreur sauvegarde historique import: {}", e.getMessage());
        }
    }
}
