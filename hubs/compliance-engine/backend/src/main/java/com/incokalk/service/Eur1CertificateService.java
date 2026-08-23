package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.Eur1Certificate;
import com.incokalk.model.TradeAgreement;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.Eur1CertificateRepository;
import com.incokalk.repository.TradeAgreementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class Eur1CertificateService {

    private final Eur1CertificateRepository eur1Repository;
    private final CompanyRepository companyRepository;
    private final TradeAgreementRepository tradeAgreementRepository;

    @Transactional
    public Eur1Certificate create(UUID companyId, Eur1Certificate dto) {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found"));

        tradeAgreementRepository.findByCode(dto.getAgreementCode())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Trade agreement not found: " + dto.getAgreementCode()));

        String certificateNumber = generateCertificateNumber();

        Eur1Certificate cert = Eur1Certificate.builder()
            .company(company)
            .certificateNumber(certificateNumber)
            .agreementCode(dto.getAgreementCode())
            .originCountry(dto.getOriginCountry().toUpperCase())
            .importerName(dto.getImporterName())
            .exporterName(dto.getExporterName())
            .hsCode(dto.getHsCode())
            .goodsDescription(dto.getGoodsDescription())
            .netWeightKg(dto.getNetWeightKg())
            .grossWeightKg(dto.getGrossWeightKg())
            .originCriteria(dto.getOriginCriteria())
            .productionMethod(dto.getProductionMethod())
            .status(Eur1Certificate.CertificateStatus.ISSUED)
            .issueDate(LocalDate.now())
            .validUntil(dto.getValidUntil() != null ? dto.getValidUntil() : LocalDate.now().plusYears(1))
            .issuerName(dto.getIssuerName())
            .notes(dto.getNotes())
            .build();

        Eur1Certificate saved = eur1Repository.save(cert);
        log.info("EUR.1 certificate created: {} for company {}", certificateNumber, companyId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Eur1Certificate> list(UUID companyId) {
        return eur1Repository.findByCompanyIdOrderByIssueDateDesc(companyId);
    }

    @Transactional(readOnly = true)
    public Eur1Certificate get(UUID companyId, UUID id) {
        return eur1Repository.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "EUR.1 certificate not found: " + id));
    }

    @Transactional
    public void delete(UUID companyId, UUID id) {
        Eur1Certificate cert = get(companyId, id);
        eur1Repository.delete(cert);
        log.info("EUR.1 certificate deleted: {} from company {}", cert.getCertificateNumber(), companyId);
    }

    @Transactional(readOnly = true)
    public CertificateValidation validate(UUID companyId, UUID id) {
        Eur1Certificate cert = get(companyId, id);

        boolean isExpired = cert.getValidUntil() != null && cert.getValidUntil().isBefore(LocalDate.now());

        boolean agreementActive = tradeAgreementRepository.findByCode(cert.getAgreementCode())
            .map(TradeAgreement::isActive)
            .orElse(false);

        boolean isValid = cert.getStatus() == Eur1Certificate.CertificateStatus.ISSUED
            && !isExpired
            && agreementActive;

        String message;
        if (isExpired) {
            message = "Certificate has expired on " + cert.getValidUntil();
        } else if (!agreementActive) {
            message = "Trade agreement " + cert.getAgreementCode() + " is no longer active";
        } else if (cert.getStatus() != Eur1Certificate.CertificateStatus.ISSUED) {
            message = "Certificate status is " + cert.getStatus() + ", not ISSUED";
        } else {
            message = "Certificate is valid and eligible for preferential tariff treatment";
        }

        return new CertificateValidation(isValid, isExpired, agreementActive, message);
    }

    public record CertificateValidation(
        boolean valid,
        boolean expired,
        boolean agreementActive,
        String message
    ) {}

    private String generateCertificateNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = eur1Repository.count() + 1;
        return String.format("EUR.1-%s-%04d", datePart, count);
    }
}
