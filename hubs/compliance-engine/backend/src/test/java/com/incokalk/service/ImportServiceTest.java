package com.incokalk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ImportService — Tests unitaires")
class ImportServiceTest {

    @InjectMocks ImportService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private MultipartFile mockMultipartFile(String content) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(file.getInputStream()).thenReturn(is);
        return file;
    }

    // importCarriersCsv a ete retiree avec le domaine Transport (CarrierRepository).

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
