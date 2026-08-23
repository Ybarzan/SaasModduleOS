package com.incokalk.service;

import com.incokalk.model.*;
import com.incokalk.repository.*;
import com.incokalk.scheduling.DistributedJobLock;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailIntakeService {

    private final EmailIntakeRepository intakeRepo;
    private final CompanyRepository companyRepo;
    private final ShipmentOrderRepository shipmentRepo;
    private final ClientUserRepository clientUserRepo;
    private final EmailMailboxRepository mailboxRepo;
    private final EmailIntakeLogRepository logRepo;
    private final CredentialEncryptionService encryptionService;
    private final DistributedJobLock jobLock;

    // Les alternatives courtes ("a", "à", "de", "to"...) doivent être encadrées de \b :
    // sans ça, elles matchent aussi au milieu d'un mot ordinaire (ex: "Paris" contient un
    // "a", "Toronto" contient "to"), ce qui tronque la capture bien avant la fin du nom de
    // ville. UNICODE_CHARACTER_CLASS force \b/\w à traiter "à" comme un caractère de mot
    // (le comportement par défaut de Java est ASCII-only), et UNICODE_CASE fait matcher
    // "à"/"À" entre eux en case-insensitive.
    private static final int NAME_FIELD_FLAGS =
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern ORIGIN_PATTERN = Pattern.compile(
        "\\b(?:origine|origin|from|de|depart)\\b[\\s:]*([A-Za-zÀ-ÿ\\s,]+?)"
            + "(?:\\s*(?:\\bpour\\b|\\bto\\b|\\bdestination\\b|\\bvers\\b|\\bà\\b|\\ba\\b|/|$))",
        NAME_FIELD_FLAGS);

    private static final Pattern DESTINATION_PATTERN = Pattern.compile(
        "\\b(?:destination|to|vers|pour|à|a)\\b[\\s:]*([A-Za-zÀ-ÿ\\s,]+?)"
            + "(?:\\s*(?:\\bpoids\\b|\\bweight\\b|\\bmarchandise\\b|\\bgoods\\b|\\bprix\\b|\\bprice\\b|$))",
        NAME_FIELD_FLAGS);

    private static final Pattern WEIGHT_PATTERN = Pattern.compile(
        "(?:poids|weight|kg)[\\s:]*([\\d.,]+)\\s*(?:kg|kilos|tonnes)?",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern VOLUME_PATTERN = Pattern.compile(
        "(?:volume|vol|m3)[\\s:]*([\\d.,]+)\\s*(?:m3|cbm)?",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern GOODS_PATTERN = Pattern.compile(
        "(?:marchandise|goods|produit|product|marchandises|commodity|description)[\\s:]*([A-Za-zÀ-ÿ\\s,.-]+?)(?:\r?\n|$)",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    @Scheduled(fixedDelayString = "${incokalk.email-intake.poll-interval:60000}")
    public void pollEmails() {
        jobLock.runExclusively("email-intake-poll", Duration.ofMinutes(3), () -> {
            for (EmailMailbox mailbox : mailboxRepo.findByIsActiveTrue()) {
                try {
                    pollMailbox(mailbox);
                } catch (Exception e) {
                    log.error("[EmailIntake] Erreur inattendue pour la boîte {}: {}", mailbox.getEmail(), e.getMessage());
                }
            }
        });
    }

    private void pollMailbox(EmailMailbox mailbox) {
        LocalDateTime startedAt = LocalDateTime.now();
        int processed = 0;
        int errors = 0;
        String protocol = mailbox.getProtocol() == EmailMailbox.Protocol.POP3 ? "pop3s" : "imaps";

        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", protocol);
            props.put("mail." + protocol + ".host", mailbox.getImapHost());
            props.put("mail." + protocol + ".port", mailbox.getImapPort());
            props.put("mail." + protocol + ".ssl.enable", String.valueOf(mailbox.getSslEnabled()));

            Session session = Session.getInstance(props);
            Store store = session.getStore(protocol);
            store.connect(mailbox.getImapHost(), mailbox.getUsername(), encryptionService.decrypt(mailbox.getEncryptedPassword()));

            Folder inbox = store.getFolder(mailbox.getFolder());
            inbox.open(Folder.READ_WRITE);

            List<Message> unread = new ArrayList<>();
            for (Message m : inbox.getMessages()) {
                if (!m.isSet(Flags.Flag.SEEN)) {
                    unread.add(m);
                }
            }

            for (Message msg : unread) {
                try {
                    processMessage(msg, mailbox);
                    if (mailbox.getDeleteAfterImport()) {
                        msg.setFlag(Flags.Flag.DELETED, true);
                    } else {
                        msg.setFlag(Flags.Flag.SEEN, true);
                    }
                    processed++;
                } catch (Exception e) {
                    log.error("[EmailIntake] Erreur traitement message ({}): {}", mailbox.getEmail(), e.getMessage());
                    errors++;
                }
            }

            inbox.close(mailbox.getDeleteAfterImport());
            store.close();

            mailbox.setLastCheckAt(LocalDateTime.now());
            mailbox.setLastError(null);
            mailboxRepo.save(mailbox);

            saveLog(mailbox, errors == 0 ? EmailIntakeLog.LogStatus.SUCCESS : EmailIntakeLog.LogStatus.PARTIAL,
                    processed + " email(s) traité(s), " + errors + " erreur(s)", processed, errors, startedAt);
        } catch (Exception e) {
            log.error("[EmailIntake] Erreur connexion {} pour {}: {}", protocol, mailbox.getEmail(), e.getMessage());
            mailbox.setLastCheckAt(LocalDateTime.now());
            mailbox.setLastError(e.getMessage() != null ? e.getMessage() : "Erreur de connexion inconnue");
            mailboxRepo.save(mailbox);

            saveLog(mailbox, EmailIntakeLog.LogStatus.FAILED, mailbox.getLastError(), processed, errors, startedAt);
        }
    }

    private void saveLog(EmailMailbox mailbox, EmailIntakeLog.LogStatus status, String message,
                          int processed, int errors, LocalDateTime startedAt) {
        logRepo.save(EmailIntakeLog.builder()
                .mailbox(mailbox)
                .status(status)
                .message(message)
                .processedCount(processed)
                .errorCount(errors)
                .startedAt(startedAt)
                .completedAt(LocalDateTime.now())
                .build());
    }

    public EmailIntake processMessage(Message msg, EmailMailbox mailbox) throws Exception {
        String from = InternetAddress.toString(msg.getFrom());
        String subject = msg.getSubject() != null ? msg.getSubject() : "";
        String body = getTextFromMessage(msg);

        if (body == null || body.isBlank()) {
            log.warn("[EmailIntake] Email vide ignore: {}", subject);
            return null;
        }

        String email = extractEmail(from);
        ClientUser client = email != null ? clientUserRepo.findByEmail(email).orElse(null) : null;

        Map<String, Object> parsed = parseEmailBody(body);

        EmailIntake intake = EmailIntake.builder()
            .senderEmail(email != null ? email : from)
            .senderName(extractSenderName(from))
            .subject(subject)
            .bodyPreview(body.length() > 500 ? body.substring(0, 500) : body)
            .origin((String) parsed.get("origin"))
            .destination((String) parsed.get("destination"))
            .goodsDescription((String) parsed.get("goods"))
            .estimatedWeight(parsed.containsKey("weight") ? (BigDecimal) parsed.get("weight") : null)
            .estimatedVolume(parsed.containsKey("volume") ? (BigDecimal) parsed.get("volume") : null)
            .matchedClientId(client != null ? client.getId() : null)
            .matchedCompanyId(client != null ? client.getCompany().getId() : mailbox.getCompany().getId())
            .mailboxId(mailbox.getId())
            .status(EmailIntake.IntakeStatus.PARSED)
            .receivedAt(LocalDateTime.now())
            .build();

        intakeRepo.save(intake);

        if (client != null && intake.getOrigin() != null && intake.getDestination() != null) {
            autoCreateDraftShipment(intake);
        }

        log.info("[EmailIntake] Email traite: sujet='{}', expediteur='{}'",
            subject, intake.getSenderEmail());

        return intake;
    }

    private Map<String, Object> parseEmailBody(String body) {
        Map<String, Object> result = new LinkedHashMap<>();

        Matcher originMatcher = ORIGIN_PATTERN.matcher(body);
        if (originMatcher.find()) {
            result.put("origin", originMatcher.group(1).trim());
        }

        Matcher destMatcher = DESTINATION_PATTERN.matcher(body);
        if (destMatcher.find()) {
            result.put("destination", destMatcher.group(1).trim());
        }

        Matcher weightMatcher = WEIGHT_PATTERN.matcher(body);
        if (weightMatcher.find()) {
            try {
                String w = weightMatcher.group(1).replace(",", ".");
                result.put("weight", new BigDecimal(w));
            } catch (NumberFormatException e) {
                log.warn("[EmailIntake] Poids invalide: {}", weightMatcher.group(1));
            }
        }

        Matcher volumeMatcher = VOLUME_PATTERN.matcher(body);
        if (volumeMatcher.find()) {
            try {
                String v = volumeMatcher.group(1).replace(",", ".");
                result.put("volume", new BigDecimal(v));
            } catch (NumberFormatException e) {
                log.warn("[EmailIntake] Volume invalide: {}", volumeMatcher.group(1));
            }
        }

        Matcher goodsMatcher = GOODS_PATTERN.matcher(body);
        if (goodsMatcher.find()) {
            result.put("goods", goodsMatcher.group(1).trim());
        }

        return result;
    }

    @Transactional
    public void autoCreateDraftShipment(EmailIntake intake) {
        if (intake.getMatchedCompanyId() == null) return;

        Company company = companyRepo.findById(intake.getMatchedCompanyId()).orElse(null);
        if (company == null) return;

        ShipmentOrder draft = new ShipmentOrder();
        draft.setCompany(company);
        draft.setShipperCountry(intake.getOrigin());
        draft.setConsigneeCountry(intake.getDestination());
        draft.setGoodsDescription(intake.getGoodsDescription());
        draft.setWeightKg(intake.getEstimatedWeight() != null ? intake.getEstimatedWeight().doubleValue() : null);
        draft.setVolumeM3(intake.getEstimatedVolume() != null ? intake.getEstimatedVolume().doubleValue() : null);
        draft.setStatus(ShipmentOrder.Status.DRAFT);
        draft.setOrderNumber("DRAFT-" + System.currentTimeMillis() % 1000000);
        draft.setCurrency("EUR");

        shipmentRepo.save(draft);

        intake.setStatus(EmailIntake.IntakeStatus.SHIPMENT_CREATED);
        intake.setCreatedShipmentId(draft.getId());
        intakeRepo.save(intake);

        log.info("[EmailIntake] Brouillon expediteur cree: {} (intake={})", draft.getOrderNumber(), intake.getId());
    }

    @Transactional(readOnly = true)
    public List<EmailIntake> getIntakeHistory(UUID companyId) {
        return intakeRepo.findByMatchedCompanyIdOrderByReceivedAtDesc(companyId);
    }

    @Transactional(readOnly = true)
    public List<EmailIntake> getPendingIntakes() {
        return intakeRepo.findByStatus(EmailIntake.IntakeStatus.PARSED);
    }

    @Transactional(readOnly = true)
    public EmailIntake getIntake(UUID id) {
        return intakeRepo.findById(id).orElse(null);
    }

    @Transactional
    public EmailIntake confirmIntake(UUID id, UUID companyId) {
        EmailIntake intake = intakeRepo.findById(id).orElse(null);
        if (intake == null) return null;

        intake.setMatchedCompanyId(companyId);
        intake.setStatus(EmailIntake.IntakeStatus.CONFIRMED);
        intakeRepo.save(intake);

        autoCreateDraftShipment(intake);
        return intake;
    }

    @Transactional
    public void rejectIntake(UUID id) {
        intakeRepo.findById(id).ifPresent(intake -> {
            intake.setStatus(EmailIntake.IntakeStatus.REJECTED);
            intakeRepo.save(intake);
        });
    }

    public Map<String, Object> getStats() {
        long total = intakeRepo.count();
        long parsed = intakeRepo.countByStatus(EmailIntake.IntakeStatus.PARSED);
        long created = intakeRepo.countByStatus(EmailIntake.IntakeStatus.SHIPMENT_CREATED);
        long confirmed = intakeRepo.countByStatus(EmailIntake.IntakeStatus.CONFIRMED);

        return Map.of(
            "total", total,
            "parsed", parsed,
            "shipmentCreated", created,
            "confirmed", confirmed
        );
    }

    private String getTextFromMessage(Message message) throws Exception {
        if (message.isMimeType("text/plain")) {
            return (String) message.getContent();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            return getTextFromMimeMultipart(mimeMultipart);
        }
        return "";
    }

    private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < mimeMultipart.getCount(); i++) {
            Part part = mimeMultipart.getBodyPart(i);
            if (part.isMimeType("text/plain")) {
                text.append(part.getContent());
            } else if (part.isMimeType("multipart/*")) {
                text.append(getTextFromMimeMultipart((MimeMultipart) part.getContent()));
            }
        }
        return text.toString();
    }

    private String extractEmail(String from) {
        if (from == null) return null;
        Matcher m = EMAIL_PATTERN.matcher(from);
        return m.find() ? m.group(1) : null;
    }

    private String extractSenderName(String from) {
        if (from == null) return "";
        int idx = from.indexOf('<');
        return idx > 0 ? from.substring(0, idx).trim() : from;
    }
}
