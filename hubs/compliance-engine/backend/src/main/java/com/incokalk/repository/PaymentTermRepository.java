package com.incokalk.repository;

import com.incokalk.model.PaymentTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTermRepository extends JpaRepository<PaymentTerm, UUID> {

    List<PaymentTerm> findByCompanyIdAndIsActiveTrue(UUID companyId);

    Optional<PaymentTerm> findByCompanyIdAndId(UUID companyId, UUID id);

    Optional<PaymentTerm> findByCompanyIdAndCode(UUID companyId, String code);

    Optional<PaymentTerm> findByCompanyIdAndIsDefaultTrue(UUID companyId);
}
