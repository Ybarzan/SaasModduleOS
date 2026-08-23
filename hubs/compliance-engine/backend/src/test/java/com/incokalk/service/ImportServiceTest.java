package com.incokalk.service;

import com.incokalk.model.Carrier;
import com.incokalk.model.Company;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ImportService — Tests unitaires")
class ImportServiceTest {

    @Mock CarrierRepository carrierRepo;
    @Mock ShipmentOrderRepository shipmentRepo;
    @Mock CompanyRepository companyRepo;

    @InjectMocks ImportService service;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
    }

    private MultipartFile mockMultipartFile(String content) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(file.getInputStream()).thenReturn(is);
        return file;
    }

    // ── importCarriersCsv ────────────────────────────────────────────────

    @Test
    @DisplayName("Import carriers CSV valide → imported = nombre de lignes")
    void importCarriersCsv_success() throws Exception {
        String csv = """
                Nom,Code,Modes,Contact,Email,Phone,Pays
                DHL Express,DHL,AIR,Jean Dupont,jd@dhl.com,+33123456,FRA
                Maersk,MSK,SEA,Pierre Martin,pm@maersk.com,+45123456,DNK
                """;

        MultipartFile file = mockMultipartFile(csv);
        when(carrierRepo.existsByCompanyIdAndCodeIgnoreCase(any(), any())).thenReturn(false);
        Company company = Company.builder().id(companyId).name("TestCo").slug("testco").build();
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);

        Map<String, Object> result = service.importCarriersCsv(file, companyId);

        assertThat(result.get("imported")).isEqualTo(2);
        assertThat(result.get("skipped")).isEqualTo(0);
        verify(carrierRepo, times(2)).save(any());
    }

    @Test
    @DisplayName("Import carriers CSV avec doublons → doublons ignorés")
    void importCarriersCsv_duplicateCode_skipped() throws Exception {
        String csv = """
                Nom,Code,Modes
                DHL Express,DHL,AIR
                """;

        MultipartFile file = mockMultipartFile(csv);

        when(carrierRepo.existsByCompanyIdAndCodeIgnoreCase(companyId, "DHL")).thenReturn(true);

        Map<String, Object> result = service.importCarriersCsv(file, companyId);

        assertThat(result.get("imported")).isEqualTo(0);
        assertThat(result.get("skipped")).isEqualTo(1);
        verify(carrierRepo, never()).save(any());
    }

    @Test
    @DisplayName("Import carriers CSV avec ligne vide → ligne ignorée avec erreur")
    void importCarriersCsv_blankFields_skipped() throws Exception {
        String csv = """
                Nom,Code,Modes
                ,DHL,AIR
                """;

        MultipartFile file = mockMultipartFile(csv);
        when(carrierRepo.existsByCompanyIdAndCodeIgnoreCase(any(), any())).thenReturn(false);

        Map<String, Object> result = service.importCarriersCsv(file, companyId);

        assertThat(result.get("imported")).isEqualTo(0);
        assertThat(result.get("skipped")).isEqualTo(1);
        assertThat((java.util.List<?>) result.get("errors")).hasSize(1);
    }

    // ── previewCsv ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Preview CSV retourne headers et 5 premières lignes")
    void previewCsv_returnsHeadersAndFirstRows() throws Exception {
        String csv = """
                Col1,Col2,Col3
                A,B,C
                D,E,F
                G,H,I
                """;

        MultipartFile file = mockMultipartFile(csv);

        Map<String, Object> result = service.previewCsv(file);

        String[] headers = (String[]) result.get("headers");
        assertThat(headers).containsExactly("Col1", "Col2", "Col3");

        java.util.List<String[]> preview = (java.util.List<String[]>) result.get("preview");
        assertThat(preview).hasSize(3);
        assertThat(preview.get(0)).containsExactly("A", "B", "C");
        assertThat(result.get("totalRows")).isEqualTo(3);
    }

    @Test
    @DisplayName("Preview CSV vide retourne headers vides et totalRows = 0")
    void previewCsv_emptyFile() throws Exception {
        String csv = "Col1,Col2\n";

        MultipartFile file = mockMultipartFile(csv);

        Map<String, Object> result = service.previewCsv(file);

        String[] headers = (String[]) result.get("headers");
        assertThat(headers).containsExactly("Col1", "Col2");

        java.util.List<String[]> preview = (java.util.List<String[]>) result.get("preview");
        assertThat(preview).isEmpty();
        assertThat(result.get("totalRows")).isEqualTo(0);
    }

    @Test
    @DisplayName("Preview CSV avec plus de 5 lignes → limite à 5 lignes de preview")
    void previewCsv_limitsToFiveRows() throws Exception {
        StringBuilder csv = new StringBuilder("H1,H2\n");
        for (int i = 1; i <= 8; i++) {
            csv.append("R").append(i).append("A,R").append(i).append("B\n");
        }

        MultipartFile file = mockMultipartFile(csv.toString());

        Map<String, Object> result = service.previewCsv(file);

        java.util.List<String[]> preview = (java.util.List<String[]>) result.get("preview");
        assertThat(preview).hasSize(5);
        assertThat(preview.get(0)).containsExactly("R1A", "R1B");
        assertThat(preview.get(4)).containsExactly("R5A", "R5B");
        assertThat(result.get("totalRows")).isEqualTo(5);
    }
}
