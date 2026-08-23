package com.incokalk.repository;

import com.incokalk.model.ParsedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParsedDocumentRepository extends JpaRepository<ParsedDocument, UUID> {

    List<ParsedDocument> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<ParsedDocument> findByCompanyIdAndId(UUID companyId, UUID id);

    List<ParsedDocument> findByCompanyIdAndDocumentTypeOrderByCreatedAtDesc(UUID companyId, ParsedDocument.DocumentType documentType);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndStatus(UUID companyId, ParsedDocument.ParseStatus status);
}
