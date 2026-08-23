package com.incokalk.service;

import com.incokalk.config.StorageConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final StorageConfig config;
    private S3Client s3;
    private S3Presigner presigner;

    public FileStorageService(StorageConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                config.getAccessKey(), config.getSecretKey());

            s3 = S3Client.builder()
                .endpointOverride(URI.create(config.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.EU_WEST_1)
                .forcePathStyle(true)
                .build();

            presigner = S3Presigner.builder()
                .endpointOverride(URI.create(config.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.EU_WEST_1)
                .build();

            ensureBuckets();
            log.info("[Storage] MinIO connecté: {}", config.getEndpoint());
        } catch (Exception e) {
            log.warn("[Storage] MinIO indisponible, stockage fichiers désactivé: {}", e.getMessage());
            s3 = null;
            presigner = null;
        }
    }

    // ── Upload ─────────────────────────────────────────────────────────

    public String uploadDocument(String fileName, byte[] data, String contentType) {
        return upload(config.getBucketDocuments(), "documents/" + fileName, data, contentType);
    }

    public String uploadLogo(UUID companyId, String originalName, byte[] data, String contentType) {
        String ext = extractExtension(originalName);
        String key = "logos/" + companyId + "/" + UUID.randomUUID() + ext;
        return upload(config.getBucketLogos(), key, data, contentType);
    }

    public String uploadLogoAndGetUrl(UUID companyId, String originalName, byte[] data, String contentType) {
        String key = uploadLogo(companyId, originalName, data, contentType);
        return getPublicUrl(config.getBucketLogos(), key);
    }

    public String uploadPdf(String category, String reference, byte[] pdfData) {
        String key = category + "/" + reference + ".pdf";
        return upload(config.getBucketDocuments(), key, pdfData, "application/pdf");
    }

    // ── Download / URL ────────────────────────────────────────────────

    public byte[] download(String bucket, String key) {
        if (s3 == null) throw new IllegalStateException("Stockage fichiers non disponible");

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
            return s3.getObjectAsBytes(request).asByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur téléchargement: " + key, e);
        }
    }

    public String getPresignedUrl(String bucket, String key, Duration expiry) {
        if (presigner == null) {
            return config.getPublicEndpoint() + "/" + bucket + "/" + key;
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(request)
                .build();

            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (Exception e) {
            return config.getPublicEndpoint() + "/" + bucket + "/" + key;
        }
    }

    public String getPublicUrl(String bucket, String key) {
        return config.getPublicEndpoint() + "/" + bucket + "/" + key;
    }

    // ── Delete ────────────────────────────────────────────────────────

    public void delete(String bucket, String key) {
        if (s3 == null) return;

        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        } catch (Exception e) {
            log.warn("[Storage] Erreur suppression {}/{}: {}", bucket, key, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String upload(String bucket, String key, byte[] data, String contentType) {
        if (s3 == null) throw new IllegalStateException("Stockage fichiers non disponible");

        try {
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build(),
                software.amazon.awssdk.core.sync.RequestBody.fromInputStream(
                    new ByteArrayInputStream(data), data.length));

            log.debug("[Storage] Upload {}/{} ({} bytes)", bucket, key, data.length);
            return key;
        } catch (Exception e) {
            throw new RuntimeException("Erreur upload vers MinIO: " + e.getMessage(), e);
        }
    }

    private void ensureBuckets() {
        try {
            createBucketIfNotExists(config.getBucketDocuments());
            createBucketIfNotExists(config.getBucketLogos());
        } catch (Exception e) {
            log.warn("[Storage] Impossible de créer les buckets: {}", e.getMessage());
        }
    }

    private void createBucketIfNotExists(String bucketName) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("[Storage] Bucket créé: {}", bucketName);
            }
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return ".png";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot) : ".png";
    }
}
