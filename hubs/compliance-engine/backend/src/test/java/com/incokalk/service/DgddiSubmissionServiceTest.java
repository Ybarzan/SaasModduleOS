package com.incokalk.service;

import com.incokalk.model.DebDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DgddiSubmissionService — Tests unitaires")
class DgddiSubmissionServiceTest {

    private final DgddiSubmissionService service = new DgddiSubmissionService(null);

    @Test
    @DisplayName("createFromDeb → construit un en-tête et une valuation à partir de la déclaration DEB")
    void createFromDeb_buildsHeaderAndValuation() {
        DebDeclaration deb = DebDeclaration.builder()
                .declarationNumber("DEB-2026-001")
                .partnerCountry("DE")
                .statisticalValue(new BigDecimal("12500.50"))
                .build();
        DgddiSubmissionService.DgddiItem item = DgddiSubmissionService.DgddiItem.builder()
                .lineNumber(1)
                .hsCode("850110")
                .statisticalValue(new BigDecimal("12500.50"))
                .quantity(BigDecimal.TEN)
                .build();

        DgddiSubmissionService.DgddiSubmission submission =
                service.createFromDeb(deb, "FR12345678901", List.of(item));

        assertThat(submission.getId()).isNotNull();
        assertThat(submission.getStatus()).isEqualTo("READY");
        assertThat(submission.getSubmittedAt()).isNotNull();
        assertThat(submission.getItems()).containsExactly(item);

        DgddiSubmissionService.DgddiHeader header = submission.getHeader();
        assertThat(header.getSenderEori()).isEqualTo("FR12345678901");
        assertThat(header.getRecipientId()).isEqualTo("FR DOUANES");
        assertThat(header.getDeclarationReference()).isEqualTo("DEB-2026-001");
        assertThat(header.getCountryOfExport()).isEqualTo("DE");
        assertThat(header.getCountryOfOrigin()).isEqualTo("DE");
        assertThat(header.getCountryOfDestination()).isEqualTo("FR");

        assertThat(submission.getValuation().getCifValue()).isEqualByComparingTo("12500.50");
        assertThat(submission.getValuation().getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("generateEdifactMessage → produit un message EDIFACT avec les segments attendus")
    void generateEdifactMessage_includesExpectedSegments() {
        DebDeclaration deb = DebDeclaration.builder()
                .declarationNumber("DEB-2026-002")
                .partnerCountry("BE")
                .statisticalValue(new BigDecimal("999.00"))
                .build();
        DgddiSubmissionService.DgddiItem item = DgddiSubmissionService.DgddiItem.builder()
                .lineNumber(1)
                .hsCode("640319")
                .statisticalValue(new BigDecimal("999.00"))
                .quantity(new BigDecimal("5"))
                .build();
        DgddiSubmissionService.DgddiSubmission submission =
                service.createFromDeb(deb, "FR98765432100", List.of(item));

        String edifact = service.generateEdifactMessage(submission);

        assertThat(edifact).startsWith("UNH+1+CUSTOMS:D:95B:UN'");
        assertThat(edifact).contains("BGM+IMEX+DEB-2026-002+9'");
        assertThat(edifact).contains("NAD+MS+FR98765432100'");
        assertThat(edifact).contains("LOC+9+BE'");
        assertThat(edifact).contains("LOC+11+FR'");
        assertThat(edifact).contains("LIN+1+640319'");
        assertThat(edifact).contains("QTY+5'");
        assertThat(edifact).endsWith("UNT+9+1'");
    }

    @Test
    @DisplayName("generateEdifactMessage → gère une liste d'articles vide sans erreur")
    void generateEdifactMessage_noItems_doesNotThrow() {
        DgddiSubmissionService.DgddiSubmission submission = DgddiSubmissionService.DgddiSubmission.builder()
                .header(DgddiSubmissionService.DgddiHeader.builder()
                        .declarationType("EX")
                        .declarationReference("DEB-EMPTY")
                        .senderEori("FR00000000000")
                        .build())
                .items(List.of())
                .build();

        String edifact = service.generateEdifactMessage(submission);

        assertThat(edifact).contains("UNT+5+1'");
    }
}
