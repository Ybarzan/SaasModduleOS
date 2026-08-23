package com.incokalk.service.ocr;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

@Slf4j
@Service
public class OcrService {

    /**
     * Extrait le texte d'un fichier (PDF, image TXT, JPG, PNG, etc.)
     */
    public Optional<String> extractText(MultipartFile file) {
        if (file == null || file.isEmpty()) return Optional.empty();

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        try {
            if (contentType != null && contentType.contains("pdf")) {
                return extractTextFromPdf(file);
            } else if (contentType != null && contentType.contains("text")) {
                return Optional.of(new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } else if (isImage(filename, contentType)) {
                return extractTextFromImage(file);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("[OCR] Erreur extraction: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> extractTextFromPdf(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            try (var doc = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setStartPage(1);
                stripper.setEndPage(doc.getNumberOfPages());

                String text = stripper.getText(doc);
                if (text != null && !text.isBlank()) {
                    return Optional.of(text.trim());
                }
            }
            log.warn("[OCR] Aucun texte extrait du PDF (peut-etre une image scannée)");
            return Optional.empty();
        } catch (IOException e) {
            log.error("[OCR] Erreur lecture PDF: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> extractTextFromImage(MultipartFile file) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(file.getBytes());
            BufferedImage image = ImageIO.read(bais);

            if (image == null) {
                log.warn("[OCR] Impossible de lire l'image");
                return Optional.empty();
            }

            String text = tesseractOcr(image);
            if (text != null && !text.isBlank()) {
                return Optional.of(text.trim());
            }

            log.warn("[OCR] Aucun texte detecte dans l'image");
            return Optional.empty();
        } catch (Exception e) {
            log.error("[OCR] Erreur OCR image: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String tesseractOcr(BufferedImage image) {
        try {
            Class<?> tesseractClass = Class.forName("net.sourceforge.tess4j.Tesseract");
            Object tesseract = tesseractClass.getDeclaredConstructor().newInstance();
            tesseractClass.getMethod("setLanguage", String.class).invoke(tesseract, "fra+eng");
            tesseractClass.getMethod("setDatapath", String.class).invoke(tesseract, getTessDataPath());
            Object result = tesseractClass.getMethod("doOCR", BufferedImage.class).invoke(tesseract, image);
            return result != null ? result.toString().trim() : null;
        } catch (ClassNotFoundException e) {
            log.warn("[OCR] Tess4J non disponible sur le classpath");
            return null;
        } catch (Exception e) {
            log.warn("[OCR] Erreur Tesseract: {} (Tesseract natif peut ne pas etre installe)", e.getMessage());
            return null;
        }
    }

    private String getTessDataPath() {
        String path = System.getenv("TESSDATA_PREFIX");
        if (path != null && !path.isBlank()) return path;
        String userDir = System.getProperty("user.dir");
        return userDir + "/tessdata";
    }

    private boolean isImage(String filename, String contentType) {
        if (contentType != null) {
            return contentType.startsWith("image/");
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")
                || lower.endsWith(".bmp") || lower.endsWith(".tiff")
                || lower.endsWith(".tif");
        }
        return false;
    }
}
