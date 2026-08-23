package com.incokalk.repository;

import com.incokalk.model.Eur1Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Eur1CertificateRepository extends JpaRepository<Eur1Certificate, UUID> {
    List<Eur1Certificate> findByCompanyIdOrderByIssueDateDesc(UUID companyId);
    Optional<Eur1Certificate> findByCompanyIdAndCertificateNumber(UUID companyId, String number);
    Optional<Eur1Certificate> findByCompanyIdAndId(UUID companyId, UUID id);
    long countByCompanyId(UUID companyId);
}
