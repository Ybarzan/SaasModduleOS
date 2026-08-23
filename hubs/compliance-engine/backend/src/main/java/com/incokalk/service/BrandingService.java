package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyBranding;
import com.incokalk.repository.CompanyBrandingRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandingService {

    private final CompanyBrandingRepository brandingRepo;

    private static final Map<String, Map<String, String>> TRANSLATIONS = new LinkedHashMap<>();

    static {
        Map<String, String> fr = new LinkedHashMap<>();
        fr.put("nav.home", "Accueil");
        fr.put("nav.shipments", "Envois");
        fr.put("nav.tracking", "Suivi");
        fr.put("nav.documents", "Documents");
        fr.put("nav.invoices", "Factures");
        fr.put("nav.profile", "Profil");
        fr.put("nav.logout", "Déconnexion");
        fr.put("page.title", "Mon Portail Client");
        fr.put("shipment.ref", "Réf. envoi");
        fr.put("shipment.origin", "Origine");
        fr.put("shipment.destination", "Destination");
        fr.put("shipment.status", "Statut");
        fr.put("shipment.eta", "ETA");
        fr.put("shipment.detail", "Détail de l envoi");
        fr.put("shipment.tracking", "Suivi en temps réel");
        fr.put("shipment.no.shipments", "Aucun envoi trouvé");
        fr.put("invoice.number", "N° facture");
        fr.put("invoice.amount", "Montant");
        fr.put("invoice.date", "Date");
        fr.put("invoice.paid", "Payée");
        fr.put("invoice.pending", "En attente");
        fr.put("documents.title", "Documents");
        fr.put("status.draft", "Brouillon");
        fr.put("status.submitted", "Soumis");
        fr.put("status.in_transit", "En transit");
        fr.put("status.delivered", "Livré");
        fr.put("status.cancelled", "Annulé");
        fr.put("status.cleared", "Dédouané");
        fr.put("search.placeholder", "Rechercher...");
        fr.put("button.download", "Télécharger");
        fr.put("button.view", "Voir");
        fr.put("button.back", "Retour");
        fr.put("kpi.total.shipments", "Envois");
        fr.put("kpi.in.transit", "En transit");
        fr.put("kpi.delivered", "Livrés");
        fr.put("kpi.pending.invoices", "Factures en attente");

        Map<String, String> en = new LinkedHashMap<>();
        en.put("nav.home", "Home");
        en.put("nav.shipments", "Shipments");
        en.put("nav.tracking", "Tracking");
        en.put("nav.documents", "Documents");
        en.put("nav.invoices", "Invoices");
        en.put("nav.profile", "Profile");
        en.put("nav.logout", "Logout");
        en.put("page.title", "My Client Portal");
        en.put("shipment.ref", "Reference");
        en.put("shipment.origin", "Origin");
        en.put("shipment.destination", "Destination");
        en.put("shipment.status", "Status");
        en.put("shipment.eta", "ETA");
        en.put("shipment.detail", "Shipment Detail");
        en.put("shipment.tracking", "Real-time Tracking");
        en.put("shipment.no.shipments", "No shipments found");
        en.put("invoice.number", "Invoice #");
        en.put("invoice.amount", "Amount");
        en.put("invoice.date", "Date");
        en.put("invoice.paid", "Paid");
        en.put("invoice.pending", "Pending");
        en.put("documents.title", "Documents");
        en.put("status.draft", "Draft");
        en.put("status.submitted", "Submitted");
        en.put("status.in_transit", "In Transit");
        en.put("status.delivered", "Delivered");
        en.put("status.cancelled", "Cancelled");
        en.put("status.cleared", "Cleared");
        en.put("search.placeholder", "Search...");
        en.put("button.download", "Download");
        en.put("button.view", "View");
        en.put("button.back", "Back");
        en.put("kpi.total.shipments", "Shipments");
        en.put("kpi.in.transit", "In Transit");
        en.put("kpi.delivered", "Delivered");
        en.put("kpi.pending.invoices", "Pending Invoices");

        Map<String, String> es = new LinkedHashMap<>();
        es.put("nav.home", "Inicio");
        es.put("nav.shipments", "Envíos");
        es.put("nav.tracking", "Seguimiento");
        es.put("nav.documents", "Documentos");
        es.put("nav.invoices", "Facturas");
        es.put("nav.profile", "Perfil");
        es.put("nav.logout", "Cerrar sesión");
        es.put("page.title", "Mi Portal del Cliente");
        es.put("status.draft", "Borrador");
        es.put("status.in_transit", "En tránsito");
        es.put("status.delivered", "Entregado");
        es.put("button.download", "Descargar");

        Map<String, String> de = new LinkedHashMap<>();
        de.put("nav.home", "Startseite");
        de.put("nav.shipments", "Sendungen");
        de.put("nav.tracking", "Sendungsverfolgung");
        de.put("nav.documents", "Dokumente");
        de.put("nav.invoices", "Rechnungen");
        de.put("nav.profile", "Profil");
        de.put("nav.logout", "Abmelden");
        de.put("page.title", "Mein Kundenportal");
        de.put("status.draft", "Entwurf");
        de.put("status.in_transit", "In Transit");
        de.put("status.delivered", "Zugestellt");
        de.put("button.download", "Herunterladen");

        Map<String, String> ar = new LinkedHashMap<>();
        ar.put("nav.home", "الرئيسية");
        ar.put("nav.shipments", "الشحنات");
        ar.put("nav.tracking", "التتبع");
        ar.put("nav.documents", "المستندات");
        ar.put("nav.invoices", "الفواتير");
        ar.put("nav.profile", "الملف الشخصي");
        ar.put("nav.logout", "تسجيل الخروج");
        ar.put("page.title", "بوابة العميل");
        ar.put("status.draft", "مسودة");
        ar.put("status.in_transit", "في العبور");
        ar.put("status.delivered", "تم التسليم");
        ar.put("button.download", "تحميل");

        TRANSLATIONS.put("FR", fr);
        TRANSLATIONS.put("EN", en);
        TRANSLATIONS.put("ES", es);
        TRANSLATIONS.put("DE", de);
        TRANSLATIONS.put("AR", ar);
    }

    @Transactional(readOnly = true)
    public CompanyBranding getBranding(UUID companyId) {
        return brandingRepo.findByCompanyId(companyId)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public CompanyBranding getBrandingByDomain(String domain) {
        return brandingRepo.findByCustomDomain(domain)
            .orElse(null);
    }

    @Transactional
    public CompanyBranding saveBranding(CompanyBranding branding) {
        return brandingRepo.save(branding);
    }

    @Transactional
    public CompanyBranding updateBranding(UUID companyId, Map<String, Object> updates) {
        CompanyBranding branding = brandingRepo.findByCompanyId(companyId)
            .orElseGet(() -> {
                CompanyBranding newBranding = CompanyBranding.builder()
                    .company(Company.builder().id(companyId).build())
                    .build();
                return brandingRepo.save(newBranding);
            });

        if (updates.containsKey("logoUrl")) branding.setLogoUrl((String) updates.get("logoUrl"));
        if (updates.containsKey("logoDarkUrl")) branding.setLogoDarkUrl((String) updates.get("logoDarkUrl"));
        if (updates.containsKey("faviconUrl")) branding.setFaviconUrl((String) updates.get("faviconUrl"));
        if (updates.containsKey("primaryColor")) branding.setPrimaryColor((String) updates.get("primaryColor"));
        if (updates.containsKey("secondaryColor")) branding.setSecondaryColor((String) updates.get("secondaryColor"));
        if (updates.containsKey("accentColor")) branding.setAccentColor((String) updates.get("accentColor"));
        if (updates.containsKey("fontFamily")) branding.setFontFamily((String) updates.get("fontFamily"));
        if (updates.containsKey("customDomain")) branding.setCustomDomain((String) updates.get("customDomain"));
        if (updates.containsKey("portalTitle")) branding.setPortalTitle((String) updates.get("portalTitle"));
        if (updates.containsKey("portalTagline")) branding.setPortalTagline((String) updates.get("portalTagline"));
        if (updates.containsKey("footerText")) branding.setFooterText((String) updates.get("footerText"));
        if (updates.containsKey("customCss")) branding.setCustomCss((String) updates.get("customCss"));
        if (updates.containsKey("defaultLanguage")) branding.setDefaultLanguage((String) updates.get("defaultLanguage"));
        if (updates.containsKey("supportedLanguages")) branding.setSupportedLanguages((String) updates.get("supportedLanguages"));

        return brandingRepo.save(branding);
    }

    public Map<String, String> getTranslations(String lang) {
        String normalized = lang != null ? lang.toUpperCase() : "FR";
        return TRANSLATIONS.getOrDefault(normalized, TRANSLATIONS.get("FR"));
    }

    public Map<String, Object> getPortalConfig(UUID companyId, String lang) {
        CompanyBranding branding = getBranding(companyId);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("language", lang != null ? lang.toUpperCase() : "FR");

        if (branding != null) {
            config.put("branding", Map.of(
                "logoUrl", branding.getLogoUrl() != null ? branding.getLogoUrl() : "",
                "logoDarkUrl", branding.getLogoDarkUrl() != null ? branding.getLogoDarkUrl() : "",
                "faviconUrl", branding.getFaviconUrl() != null ? branding.getFaviconUrl() : "",
                "primaryColor", branding.getPrimaryColor(),
                "secondaryColor", branding.getSecondaryColor(),
                "accentColor", branding.getAccentColor(),
                "fontFamily", branding.getFontFamily(),
                "portalTitle", branding.getPortalTitle() != null ? branding.getPortalTitle() : "Client Portal",
                "portalTagline", branding.getPortalTagline() != null ? branding.getPortalTagline() : "",
                "footerText", branding.getFooterText() != null ? branding.getFooterText() : ""
            ));
        } else {
            config.put("branding", Map.of(
                "logoUrl", "",
                "primaryColor", "#2563EB",
                "secondaryColor", "#1E40AF",
                "accentColor", "#F59E0B",
                "fontFamily", "Inter, system-ui, sans-serif",
                "portalTitle", "Client Portal",
                "portalTagline", "",
                "footerText", ""
            ));
        }

        config.put("translations", getTranslations(config.get("language").toString()));

        return config;
    }

    public List<String> getSupportedLanguages() {
        return List.of("FR", "EN", "ES", "DE", "AR");
    }
}
