package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.CustomRole;
import com.incokalk.model.Notification;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detecte le moment ou une entreprise se structure en departements -- la
 * creation d'un role personnalise dont le nom/la description evoque un Hub
 * commercial (Douane, Entrepot, Finance...) -- et propose l'upgrade
 * correspondant si le plan actuel ne le couvre pas deja.
 *
 * C'est le mecanisme central du modele de commercialisation fusionne PME/
 * departement (voir memoire projet) : l'unite de vente bascule de "tout
 * groupe dans Starter" a "a la carte par Hub" exactement au moment ou un
 * poste dedie apparait chez le client -- pas avant, pas a toute l'equipe
 * d'un coup. Le catalogue de permissions structure (voir CustomRoleService)
 * n'a pas de notion de "module Douane/Entrepot", donc le signal repose sur
 * une correspondance de mots-cles sur le nom/description en texte libre --
 * imparfait mais transparent et facile a etendre.
 *
 * Se declenche uniquement a la creation du role (pas a son assignation a un
 * 2e/3e utilisateur -- limite de perimetre assumee, la creation est deja un
 * signal fort et volontaire).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HubUpsellSignalService {

    private final CompanyRoleRepository companyRoleRepo;
    private final NotificationRepository notificationRepo;
    private final PlanChecker planChecker;

    private record HubSignal(String hubName, Company.Plan requiredPlan, List<String> keywords) {}

    private static final List<HubSignal> SIGNALS = List.of(
        new HubSignal("Import-Export (douane approfondie)", Company.Plan.PRO, List.of(
            "douane", "customs", "conformite", "compliance", "eori", "dps",
            "screening", "eur.1", "eur1", "taric", "fiscal", "declaration")),
        new HubSignal("Entrepot & Stock", Company.Plan.ENTERPRISE, List.of(
            "entrepot", "warehouse", "stock", "receptionniste", "reception", "magasinier")),
        new HubSignal("Finance & Tresorerie", Company.Plan.ENTERPRISE, List.of(
            "finance", "tresorerie", "comptab", "facturation", "controleur", "dsi financier"))
    );

    @Transactional
    public void onCustomRoleCreated(CustomRole role) {
        String haystack = normalize((role.getName() != null ? role.getName() : "")
            + " " + (role.getDescription() != null ? role.getDescription() : ""));

        for (HubSignal signal : SIGNALS) {
            boolean matches = signal.keywords().stream().anyMatch(haystack::contains);
            if (!matches) continue;

            Company company = role.getCompany();
            if (planChecker.hasMinimumPlan(company.getId(), signal.requiredPlan())) {
                // Deja couvert par le plan actuel -- rien a proposer.
                return;
            }

            notifyOwner(company, role, signal);
            return; // un seul signal a la fois, meme si le nom en evoque plusieurs
        }
    }

    private void notifyOwner(Company company, CustomRole role, HubSignal signal) {
        List<CompanyRole> owners = companyRoleRepo.findByCompanyIdAndRole(company.getId(), CompanyRole.Role.OWNER);
        if (owners.isEmpty()) {
            log.warn("[HubUpsell] Aucun OWNER trouve pour company={}, signal ignore", company.getId());
            return;
        }
        User owner = owners.get(0).getUser();

        String title = "Un nouveau Hub pourrait vous intéresser";
        String message = String.format(
            "Vous avez créé le rôle « %s » — le Hub %s n'est pas encore inclus dans votre plan actuel. "
            + "Consultez la page tarifs pour l'activer pour votre équipe.",
            role.getName(), signal.hubName());

        Notification notification = Notification.builder()
            .company(company)
            .user(owner)
            .eventType("HUB_UPSELL_SIGNAL")
            .title(title)
            .message(message)
            .channel("IN_APP")
            .status("UNREAD")
            .sentAt(LocalDateTime.now())
            .entityType("CUSTOM_ROLE")
            .entityId(role.getId())
            .build();

        notificationRepo.save(notification);
        log.info("[HubUpsell] Signal '{}' declenche par le role '{}' pour company={}",
            signal.hubName(), role.getName(), company.getId());
    }

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static String normalize(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.FRENCH);
    }
}
