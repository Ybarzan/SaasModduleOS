package com.incokalk.service.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OcrService — Tests extraction de texte (PDF/texte/image)")
class OcrServiceTest {

    private OcrService service;

    @BeforeEach
    void setUp() {
        service = new OcrService();
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private byte[] pdfWithText(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] blankPdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] pngImageBytes() throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    // ── extractText — file null / empty ───────────────────────────────

    @Test
    @DisplayName("extractText — null file returns empty")
    void extractText_nullFile() {
        assertThat(service.extractText(null)).isEmpty();
    }

    @Test
    @DisplayName("extractText — empty file returns empty")
    void extractText_emptyFile() {
        MultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertThat(service.extractText(file)).isEmpty();
    }

    // ── extractText — routing by content type ─────────────────────────

    @Test
    @DisplayName("extractText — PDF content type routes to PDF extraction and returns text")
    void extractText_pdfContentType() throws Exception {
        byte[] pdf = pdfWithText("Hello IncoKalk");
        MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdf);

        Optional<String> result = service.extractText(file);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Hello IncoKalk");
    }

    @Test
    @DisplayName("extractText — text content type reads raw bytes as UTF-8")
    void extractText_textContentType() {
        String content = "plain text content";
        MultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));

        Optional<String> result = service.extractText(file);

        assertThat(result).contains(content);
    }

    @Test
    @DisplayName("extractText — image content type (image/*) routes to image extraction")
    void extractText_imageContentType() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "scan.png", "image/png", pngImageBytes());

        Optional<String> result = service.extractText(file);

        // No tessdata available in test environment -> tesseract yields blank text -> empty result
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractText — no content type but image filename extension routes to image extraction")
    void extractText_imageByFilenameExtension() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "scan.png", null, pngImageBytes());

        Optional<String> result = service.extractText(file);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractText — unsupported content type and extension returns empty")
    void extractText_unsupportedType() {
        MultipartFile file = new MockMultipartFile("file", "data.bin", "application/octet-stream",
                "binary".getBytes(StandardCharsets.UTF_8));

        assertThat(service.extractText(file)).isEmpty();
    }

    @Test
    @DisplayName("extractText — unknown content type and unknown extension returns empty")
    void extractText_unknownExtensionNoContentType() {
        MultipartFile file = new MockMultipartFile("file", "document.xyz", null,
                "content".getBytes(StandardCharsets.UTF_8));

        assertThat(service.extractText(file)).isEmpty();
    }

    @Test
    @DisplayName("extractText — exception during processing is caught and returns empty")
    void extractText_exceptionCaught() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getOriginalFilename()).thenReturn("broken.txt");
        when(file.getBytes()).thenThrow(new IOException("boom"));

        assertThat(service.extractText(file)).isEmpty();
    }

    // ── extractTextFromPdf ──────────────────────────────────────────────

    @Test
    @DisplayName("extractTextFromPdf — valid PDF with text returns trimmed text")
    void extractTextFromPdf_withText() throws Exception {
        byte[] pdf = pdfWithText("Facture Numero 123");
        MultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", pdf);

        Optional<String> result = service.extractTextFromPdf(file);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Facture Numero 123");
    }

    @Test
    @DisplayName("extractTextFromPdf — blank PDF (no text) returns empty")
    void extractTextFromPdf_blank() throws Exception {
        byte[] pdf = blankPdf();
        MultipartFile file = new MockMultipartFile("file", "blank.pdf", "application/pdf", pdf);

        Optional<String> result = service.extractTextFromPdf(file);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractTextFromPdf — corrupt/unreadable bytes returns empty")
    void extractTextFromPdf_corruptBytes() {
        MultipartFile file = new MockMultipartFile("file", "corrupt.pdf", "application/pdf",
                new byte[]{0, 1, 2, 3, 4, 5});

        Optional<String> result = service.extractTextFromPdf(file);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractTextFromPdf — IOException reading bytes is caught and returns empty")
    void extractTextFromPdf_getBytesThrows() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("disk error"));

        Optional<String> result = service.extractTextFromPdf(file);

        assertThat(result).isEmpty();
    }

    // ── extractTextFromImage ─────────────────────────────────────────────

    @Test
    @DisplayName("extractTextFromImage — decodable image but no OCR text detected returns empty")
    void extractTextFromImage_decodableNoText() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "scan.png", "image/png", pngImageBytes());

        Optional<String> result = service.extractTextFromImage(file);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractTextFromImage — undecodable bytes returns empty")
    void extractTextFromImage_undecodable() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "not-an-image.png", "image/png",
                new byte[]{9, 9, 9, 9, 9});

        Optional<String> result = service.extractTextFromImage(file);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("extractTextFromImage — exception reading bytes is caught and returns empty")
    void extractTextFromImage_exceptionCaught() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("read error"));

        Optional<String> result = service.extractTextFromImage(file);

        assertThat(result).isEmpty();
    }
}
