package com.incokalk.service;

import com.incokalk.model.ClientUser;
import com.incokalk.model.Company;
import com.incokalk.model.EmailIntake;
import com.incokalk.model.EmailMailbox;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.ClientUserRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EmailIntakeLogRepository;
import com.incokalk.repository.EmailIntakeRepository;
import com.incokalk.repository.EmailMailboxRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.scheduling.DistributedJobLock;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("EmailIntakeService — Tests unitaires")
class EmailIntakeServiceTest {

    EmailIntakeService service;
    EmailIntakeRepository intakeRepo;
    CompanyRepository companyRepo;
    ShipmentOrderRepository shipmentRepo;
    ClientUserRepository clientUserRepo;

    @BeforeEach
    void setUp() {
        intakeRepo = mock(EmailIntakeRepository.class);
        companyRepo = mock(CompanyRepository.class);
        shipmentRepo = mock(ShipmentOrderRepository.class);
        clientUserRepo = mock(ClientUserRepository.class);
        service = new EmailIntakeService(intakeRepo, companyRepo, shipmentRepo, clientUserRepo,
            mock(EmailMailboxRepository.class), mock(EmailIntakeLogRepository.class),
            mock(CredentialEncryptionService.class), new DistributedJobLock(Optional.empty()));
    }

    private Message mockMessage(String fromEmail, String subject, String body) throws Exception {
        Message msg = mock(Message.class);
        when(msg.getFrom()).thenReturn(new jakarta.mail.Address[]{new InternetAddress(fromEmail, "Sender Name")});
        when(msg.getSubject()).thenReturn(subject);
        when(msg.isMimeType("text/plain")).thenReturn(true);
        when(msg.getContent()).thenReturn(body);
        return msg;
    }

    private EmailMailbox mailbox(Company company) {
        return EmailMailbox.builder().id(UUID.randomUUID()).company(company).email("intake@incokalk.com").build();
    }

    @Test
    @DisplayName("getIntake → trouvé")
    void getIntake_found() {
        UUID id = UUID.randomUUID();
        EmailIntake intake = EmailIntake.builder().id(id).subject("Test").build();
        when(intakeRepo.findById(id)).thenReturn(Optional.of(intake));

        EmailIntake result = service.getIntake(id);

        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("Test");
    }

    @Test
    @DisplayName("getIntake → non trouvé → null")
    void getIntake_notFound() {
        when(intakeRepo.findById(any())).thenReturn(Optional.empty());

        assertThat(service.getIntake(UUID.randomUUID())).isNull();
    }

    @Test
    @DisplayName("getIntakeHistory → retourne liste triée")
    void getIntakeHistory() {
        UUID companyId = UUID.randomUUID();
        List<EmailIntake> intakes = List.of(
                EmailIntake.builder().subject("Email 1").build(),
                EmailIntake.builder().subject("Email 2").build()
        );
        when(intakeRepo.findByMatchedCompanyIdOrderByReceivedAtDesc(companyId)).thenReturn(intakes);

        List<EmailIntake> result = service.getIntakeHistory(companyId);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getPendingIntakes → retourne les emails PARSED")
    void getPendingIntakes() {
        List<EmailIntake> pending = List.of(
                EmailIntake.builder().status(EmailIntake.IntakeStatus.PARSED).build()
        );
        when(intakeRepo.findByStatus(EmailIntake.IntakeStatus.PARSED)).thenReturn(pending);

        List<EmailIntake> result = service.getPendingIntakes();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("confirmIntake → confirme et crée un brouillon")
    void confirmIntake() {
        UUID intakeId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        EmailIntake intake = EmailIntake.builder()
                .id(intakeId)
                .origin("Paris")
                .destination("Lyon")
                .matchedCompanyId(companyId)
                .status(EmailIntake.IntakeStatus.PARSED)
                .build();
        when(intakeRepo.findById(intakeId)).thenReturn(Optional.of(intake));
        when(intakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        EmailIntake result = service.confirmIntake(intakeId, companyId);

        assertThat(result.getStatus()).isEqualTo(EmailIntake.IntakeStatus.CONFIRMED);
        assertThat(result.getMatchedCompanyId()).isEqualTo(companyId);
    }

    @Test
    @DisplayName("confirmIntake → intake inconnu → null")
    void confirmIntake_notFound() {
        when(intakeRepo.findById(any())).thenReturn(Optional.empty());

        assertThat(service.confirmIntake(UUID.randomUUID(), UUID.randomUUID())).isNull();
    }

    @Test
    @DisplayName("rejectIntake → passe en REJECTED")
    void rejectIntake() {
        UUID id = UUID.randomUUID();
        EmailIntake intake = EmailIntake.builder()
                .id(id)
                .status(EmailIntake.IntakeStatus.PARSED)
                .build();
        when(intakeRepo.findById(id)).thenReturn(Optional.of(intake));
        when(intakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.rejectIntake(id);

        assertThat(intake.getStatus()).isEqualTo(EmailIntake.IntakeStatus.REJECTED);
    }

    @Test
    @DisplayName("rejectIntake → inexistant → ignoré")
    void rejectIntake_notFound() {
        when(intakeRepo.findById(any())).thenReturn(Optional.empty());

        service.rejectIntake(UUID.randomUUID());

        verify(intakeRepo, never()).save(any());
    }

    @Test
    @DisplayName("getStats → retourne les compteurs")
    void getStats() {
        when(intakeRepo.count()).thenReturn(10L);
        when(intakeRepo.countByStatus(EmailIntake.IntakeStatus.PARSED)).thenReturn(3L);
        when(intakeRepo.countByStatus(EmailIntake.IntakeStatus.SHIPMENT_CREATED)).thenReturn(4L);
        when(intakeRepo.countByStatus(EmailIntake.IntakeStatus.CONFIRMED)).thenReturn(2L);

        Map<String, Object> stats = service.getStats();

        assertThat(stats)
                .containsEntry("total", 10L)
                .containsEntry("parsed", 3L)
                .containsEntry("shipmentCreated", 4L)
                .containsEntry("confirmed", 2L);
    }

    @Test
    @DisplayName("processMessage → corps vide → email ignoré, retourne null")
    void processMessage_blankBody_returnsNull() throws Exception {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        EmailMailbox mailbox = mailbox(company);
        Message msg = mockMessage("someone@test.com", "Vide", "   ");

        EmailIntake result = service.processMessage(msg, mailbox);

        assertThat(result).isNull();
        verify(intakeRepo, never()).save(any());
    }

    @Test
    @DisplayName("processMessage → client connu, origine+destination trouvés → parse + brouillon auto-créé")
    void processMessage_knownClient_withOriginAndDestination_createsDraft() throws Exception {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        EmailMailbox mb = mailbox(company);
        ClientUser client = ClientUser.builder().id(UUID.randomUUID()).email("client@test.com").company(company).build();
        when(clientUserRepo.findByEmail("client@test.com")).thenReturn(Optional.of(client));
        when(companyRepo.findById(company.getId())).thenReturn(Optional.of(company));
        when(intakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String body = "Bonjour,\nOrigine: Paris\nDestination: Lyon\nPoids: 12,5 kg\nVolume: 2,3 m3\nMarchandise: Textile\n";
        Message msg = mockMessage("client@test.com", "Demande de devis", body);

        EmailIntake result = service.processMessage(msg, mb);

        assertThat(result).isNotNull();
        assertThat(result.getMatchedClientId()).isEqualTo(client.getId());
        assertThat(result.getMatchedCompanyId()).isEqualTo(company.getId());
        assertThat(result.getOrigin()).isEqualTo("Paris");
        assertThat(result.getDestination()).isEqualTo("Lyon");
        assertThat(result.getEstimatedWeight()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(result.getEstimatedVolume()).isEqualByComparingTo(new BigDecimal("2.3"));
        assertThat(result.getStatus()).isEqualTo(EmailIntake.IntakeStatus.SHIPMENT_CREATED);
        verify(shipmentRepo).save(any(ShipmentOrder.class));
    }

    @Test
    @DisplayName("processMessage → ville d'origine contenant un mot-clé terminateur en son milieu (\"Toronto\") → non tronquée")
    void processMessage_originContainingTerminatorSubstring_notTruncated() throws Exception {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        EmailMailbox mb = mailbox(company);
        when(clientUserRepo.findByEmail(any())).thenReturn(Optional.empty());
        when(intakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // "Toronto" contient "to" en son milieu (positions 5-6), lequel est aussi l'un des
        // mots-clés terminateurs du pattern — sans délimiteurs de mot (\b), la capture
        // s'arrêterait à "Toron" au lieu de "Toronto".
        String body = "Origine: Toronto\nDestination: Vancouver";
        Message msg = mockMessage("someone@test.com", "Devis Canada", body);

        EmailIntake result = service.processMessage(msg, mb);

        assertThat(result.getOrigin()).isEqualTo("Toronto");
        assertThat(result.getDestination()).isEqualTo("Vancouver");
    }

    @Test
    @DisplayName("processMessage → préposition \"À\" majuscule accentuée → toujours reconnue comme terminateur")
    void processMessage_uppercaseAccentedPreposition_stillRecognized() throws Exception {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        EmailMailbox mb = mailbox(company);
        when(clientUserRepo.findByEmail(any())).thenReturn(Optional.empty());
        when(intakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String body = "Origine: Nice À Bordeaux";
        Message msg = mockMessage("someone@test.com", "Trajet", body);

        EmailIntake result = service.processMessage(msg, mb);

        assertThat(result.getOrigin()).isEqualTo("Nice");
    }

    @Test
    @DisplayName("processMessage → expéditeur inconnu → rattaché à la company de la boîte mail, pas de brouillon si destination absente")
    void processMessage_unknownSender_fallsBackToMailboxCompany_noDestination_noDraft() throws Exception {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        EmailMailbox mb = mailbox(company);
        when(clientUserRepo.findByEmail(any())).thenReturn(Optional.empty());
        when(intakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String body = "Origine: Marseille sans destination indiquee";
        Message msg = mockMessage("inconnu@test.com", "Question", body);

        EmailIntake result = service.processMessage(msg, mb);

        assertThat(result).isNotNull();
        assertThat(result.getMatchedClientId()).isNull();
        assertThat(result.getMatchedCompanyId()).isEqualTo(company.getId());
        assertThat(result.getStatus()).isEqualTo(EmailIntake.IntakeStatus.PARSED);
        verify(shipmentRepo, never()).save(any());
    }

    @Test
    @DisplayName("processMessage → poids non numérique après capture → ignoré silencieusement, pas d'exception")
    void processMessage_invalidWeightFormat_ignoredGracefully() throws Exception {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        EmailMailbox mb = mailbox(company);
        when(clientUserRepo.findByEmail(any())).thenReturn(Optional.empty());
        when(intakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Le pattern capture des chiffres/points/virgules uniquement, donc un poids
        // "abc" ne matche pas — on force plutôt une valeur qui matche mais casse le parse
        // BigDecimal (plusieurs points), cas limite volontaire.
        String body = "Poids: 1.2.3 kg";
        Message msg = mockMessage("someone@test.com", "Poids invalide", body);

        EmailIntake result = service.processMessage(msg, mb);

        assertThat(result).isNotNull();
        assertThat(result.getEstimatedWeight()).isNull();
    }

    @Test
    @DisplayName("autoCreateDraftShipment → matchedCompanyId absent → ne fait rien")
    void autoCreateDraftShipment_noCompanyId_noop() {
        EmailIntake intake = EmailIntake.builder().id(UUID.randomUUID()).matchedCompanyId(null).build();

        service.autoCreateDraftShipment(intake);

        verify(shipmentRepo, never()).save(any());
        verify(intakeRepo, never()).save(any());
    }

    @Test
    @DisplayName("autoCreateDraftShipment → company introuvable → ne fait rien")
    void autoCreateDraftShipment_companyNotFound_noop() {
        UUID companyId = UUID.randomUUID();
        EmailIntake intake = EmailIntake.builder().id(UUID.randomUUID()).matchedCompanyId(companyId).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        service.autoCreateDraftShipment(intake);

        verify(shipmentRepo, never()).save(any());
    }

    @Test
    @DisplayName("autoCreateDraftShipment → succès sans poids ni volume estimés")
    void autoCreateDraftShipment_success_withoutWeightOrVolume() {
        UUID companyId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).build();
        EmailIntake intake = EmailIntake.builder()
                .id(UUID.randomUUID())
                .matchedCompanyId(companyId)
                .origin("Paris")
                .destination("Lyon")
                .estimatedWeight(null)
                .estimatedVolume(null)
                .build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(intakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.autoCreateDraftShipment(intake);

        verify(shipmentRepo).save(argThat((ShipmentOrder s) ->
                s.getWeightKg() == null && s.getVolumeM3() == null && s.getStatus() == ShipmentOrder.Status.DRAFT));
        assertThat(intake.getStatus()).isEqualTo(EmailIntake.IntakeStatus.SHIPMENT_CREATED);
    }
}
