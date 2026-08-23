package com.incokalk.service;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DgddiSubmissionService {

    private final DebDeclarationService debDeclarationService;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DgddiHeader {
        private String senderEori;
        private String recipientId;
        private String messageType;
        private LocalDateTime messageDate;
        private String declarationType;
        private String procedureCode;
        private String regimeCode;
        private String declarationReference;
        private String previousDocumentRef;
        private String countryOfExport;
        private String countryOfDestination;
        private String countryOfOrigin;
        private String destinationOffice;
        private String exitOffice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DgddiItem {
        private int lineNumber;
        private String hsCode;
        private String goodsDescription;
        private BigDecimal netWeightKg;
        private BigDecimal grossWeightKg;
        private BigDecimal statisticalValue;
        private String currency;
        private String countryOfOrigin;
        private String commodityCode;
        private String additionalCode;
        private BigDecimal quantity;
        private String unitOfMeasure;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DgddiValuation {
        private BigDecimal cifValue;
        private BigDecimal fobValue;
        private String currency;
        private BigDecimal exchangeRate;
        private BigDecimal freight;
        private BigDecimal insurance;
        private BigDecimal otherCharges;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DgddiDocument {
        private String documentType;
        private String documentReference;
        private String documentQualifier;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DgddiTransport {
        private String transportMode;
        private String transportIdentifier;
        private String vesselName;
        private String voyageNumber;
        private String containerNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DgddiSubmission {
        private UUID id;
        private DgddiHeader header;
        private List<DgddiItem> items;
        private DgddiValuation valuation;
        private List<DgddiDocument> documents;
        private DgddiTransport transport;
        private String status;
        private String acknowledgmentReference;
        private LocalDateTime submittedAt;
        private LocalDateTime acknowledgedAt;
        private String errorMessage;
    }

    public DgddiSubmission createFromDeb(com.incokalk.model.DebDeclaration deb,
                                          String eoriNumber,
                                          List<DgddiItem> items) {
        DgddiHeader header = DgddiHeader.builder()
                .senderEori(eoriNumber)
                .recipientId("FR DOUANES")
                .messageType("IM")
                .messageDate(LocalDateTime.now())
                .declarationType("EX")
                .procedureCode("IM01")
                .regimeCode("1100")
                .declarationReference(deb.getDeclarationNumber())
                .countryOfExport(deb.getPartnerCountry())
                .countryOfDestination("FR")
                .countryOfOrigin(deb.getPartnerCountry())
                .destinationOffice("FR000")
                .build();

        DgddiValuation valuation = DgddiValuation.builder()
                .cifValue(deb.getStatisticalValue())
                .currency("EUR")
                .build();

        DgddiSubmission submission = DgddiSubmission.builder()
                .id(UUID.randomUUID())
                .header(header)
                .items(items)
                .valuation(valuation)
                .status("READY")
                .submittedAt(LocalDateTime.now())
                .build();

        return submission;
    }

    public String generateEdifactMessage(DgddiSubmission submission) {
        StringBuilder sb = new StringBuilder();
        DgddiHeader h = submission.getHeader();

        sb.append("UNH+1+CUSTOMS:D:95B:UN'");
        sb.append("BGM+IM").append(h.getDeclarationType()).append("+")
                .append(h.getDeclarationReference()).append("+9'");
        sb.append("DTM+").append(h.getMessageDate()).append("'");
        sb.append("NAD+MS+").append(h.getSenderEori()).append("'");

        if (h.getCountryOfExport() != null) {
            sb.append("LOC+9+").append(h.getCountryOfExport()).append("'");
        }
        if (h.getCountryOfDestination() != null) {
            sb.append("LOC+11+").append(h.getCountryOfDestination()).append("'");
        }

        for (DgddiItem item : submission.getItems()) {
            sb.append("LIN+").append(item.getLineNumber()).append("+").append(item.getHsCode()).append("'");
            sb.append("QTY+").append(item.getQuantity()).append("'");
            sb.append("MOA+380+").append(item.getStatisticalValue()).append("'");
        }

        if (submission.getValuation() != null) {
            sb.append("MOA+380+").append(submission.getValuation().getCifValue()).append("'");
        }

        sb.append("UNT+").append(submission.getItems().size() * 4 + 5).append("+1'");
        return sb.toString();
    }
}
