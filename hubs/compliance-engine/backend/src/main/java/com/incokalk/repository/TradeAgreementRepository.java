package com.incokalk.repository;

import com.incokalk.model.TradeAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeAgreementRepository extends JpaRepository<TradeAgreement, UUID> {
    Optional<TradeAgreement> findByCode(String code);
    List<TradeAgreement> findByPartnerCountryAndIsActiveTrue(String partnerCountry);
    List<TradeAgreement> findByIsActiveTrue();
    List<TradeAgreement> findByHsChaptersCoveredContaining(String chapter);
}
