package com.fleethub.service.tour;

import com.fleethub.dto.tour.OrderImportResultDto;
import com.fleethub.model.Company;
import com.fleethub.model.DeliveryOrder;
import com.fleethub.model.Site;
import com.fleethub.model.TourStop;
import com.fleethub.repository.DeliveryOrderRepository;
import com.fleethub.repository.SiteRepository;
import com.fleethub.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Import des commandes du jour depuis un fichier CSV (export du logiciel de
 * gestion du client, ou Excel « Enregistrer sous… CSV »). Séparateur détecté
 * (; , tabulation), en-têtes souples, sites rapprochés ou créés, adresses
 * géocodées via la Base Adresse Nationale.
 */
@Service
@RequiredArgsConstructor
public class OrderImportService {

    static final int MAX_ROWS = 2000;
    private static final double MIN_GEOCODE_SCORE = 0.5;

    /** Alias d'en-têtes acceptés (après normalisation) → champ. */
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        alias("reference", "reference", "ref", "commande", "n commande", "numero", "bon");
        alias("siteRef", "code client", "ref site", "reference site", "code site", "finess");
        alias("name", "nom", "client", "site", "destinataire", "raison sociale", "etablissement");
        alias("address", "adresse", "rue", "adresse 1");
        alias("postalCode", "code postal", "cp", "postal");
        alias("city", "ville", "commune");
        alias("latitude", "latitude", "lat");
        alias("longitude", "longitude", "lon", "lng", "long");
        alias("type", "type", "operation", "type arret");
        alias("siteKind", "type site", "categorie", "nature");
        alias("windowStart", "debut", "creneau debut", "heure debut", "de", "ouverture");
        alias("windowEnd", "fin", "creneau fin", "heure fin", "a", "fermeture");
        alias("service", "duree", "duree min", "temps sur place");
        alias("quantity", "quantite", "colis", "nb colis", "sachets", "qte");
        alias("phone", "telephone", "tel", "contact");
        alias("notes", "notes", "commentaire", "instructions", "consignes");
        alias("tempMin", "temp min", "temperature min");
        alias("tempMax", "temp max", "temperature max");
    }

    private static void alias(String field, String... names) {
        for (String n : names) ALIASES.put(n, field);
    }

    private final DeliveryOrderRepository orderRepository;
    private final SiteRepository siteRepository;
    private final GeocodingService geocodingService;

    @Transactional
    public OrderImportResultDto importCsv(InputStream in, LocalDate date) {
        Company company = TenantContext.require();
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            lines = reader.lines().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("Fichier illisible");
        }
        if (lines.size() < 2) {
            throw new IllegalArgumentException("Fichier vide : une ligne d'en-tête et au moins une commande sont attendues");
        }
        if (lines.size() - 1 > MAX_ROWS) {
            throw new IllegalArgumentException("Fichier trop volumineux (max " + MAX_ROWS + " commandes)");
        }
        String headerLine = lines.get(0).replace("﻿", "");
        char sep = detectSeparator(headerLine);
        List<String> headers = parseLine(headerLine, sep);
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String field = ALIASES.get(OrderService.normalize(headers.get(i)));
            if (field != null) col.putIfAbsent(field, i);
        }
        if (!col.containsKey("name") && !col.containsKey("siteRef")) {
            throw new IllegalArgumentException("Colonne « nom » (ou « code client ») introuvable dans l'en-tête");
        }

        Map<String, Site> sites = OrderService.indexSites(siteRepository.findByCompanyIdOrderByNameAsc(company.getId()));
        List<String> errors = new ArrayList<>();
        int created = 0, sitesCreated = 0, geocoded = 0, withoutLocation = 0;

        for (int i = 1; i < lines.size(); i++) {
            int lineNo = i + 1;
            List<String> cells = parseLine(lines.get(i), sep);
            try {
                Row row = new Row(cells, col);
                // Validation complète de la ligne avant toute création (pas de site orphelin en cas d'erreur)
                TourStop.StopType type = stopType(row.get("type"));
                LocalTime windowStart = row.time("windowStart");
                LocalTime windowEnd = row.time("windowEnd");
                Integer service = row.integer("service");
                Integer qty = row.integer("quantity");
                Double tempMin = row.decimal("tempMin");
                Double tempMax = row.decimal("tempMax");
                row.decimal("latitude");
                row.decimal("longitude");
                Site site = findSite(sites, row);
                if (site == null && row.get("name") == null) {
                    throw new IllegalArgumentException("code client inconnu et nom absent");
                }
                LocalTime from = windowStart != null ? windowStart : site != null ? site.getOpeningFrom() : null;
                LocalTime to = windowEnd != null ? windowEnd : site != null ? site.getOpeningTo() : null;
                if (from != null && to != null && to.isBefore(from)) {
                    throw new IllegalArgumentException("fin de créneau avant le début");
                }

                if (site == null) {
                    site = newSite(company, row);
                    sitesCreated++;
                    index(sites, site);
                }
                if (!site.hasCoordinates() && geocode(site)) {
                    geocoded++;
                }
                siteRepository.save(site);
                if (!site.hasCoordinates()) withoutLocation++;

                DeliveryOrder o = new DeliveryOrder();
                o.setCompany(company);
                o.setDate(date);
                o.setSite(site);
                o.setReference(truncate(row.get("reference"), 255));
                o.setType(type);
                o.setWindowStart(from);
                o.setWindowEnd(to);
                o.setServiceMinutes(service != null ? service : site.getServiceMinutes() != null ? site.getServiceMinutes() : 5);
                o.setQuantity(qty != null ? qty : 1);
                o.setTemperatureMinCelsius(tempMin);
                o.setTemperatureMaxCelsius(tempMax);
                o.setNotes(truncate(row.get("notes"), 1000));
                orderRepository.save(o);
                created++;
            } catch (IllegalArgumentException | DateTimeParseException e) {
                if (errors.size() < 50) errors.add("Ligne " + lineNo + " : " + e.getMessage());
            }
        }
        return new OrderImportResultDto(lines.size() - 1, created, sitesCreated, geocoded, withoutLocation, errors);
    }

    private Site findSite(Map<String, Site> sites, Row row) {
        if (row.get("siteRef") != null) {
            Site s = sites.get("ref:" + OrderService.normalize(row.get("siteRef")));
            if (s != null) return s;
        }
        if (row.get("name") != null) {
            return sites.get("name:" + OrderService.normalize(row.get("name")) + "|" + OrderService.normalize(row.get("postalCode")));
        }
        return null;
    }

    private static void index(Map<String, Site> sites, Site s) {
        if (s.getReference() != null) sites.putIfAbsent("ref:" + OrderService.normalize(s.getReference()), s);
        sites.putIfAbsent("name:" + OrderService.normalize(s.getName()) + "|" + OrderService.normalize(s.getPostalCode()), s);
    }

    private static Site newSite(Company company, Row row) {
        Site s = new Site();
        s.setCompany(company);
        s.setName(truncate(row.get("name"), 255));
        s.setKind(siteKind(row.get("siteKind")));
        s.setReference(truncate(row.get("siteRef"), 255));
        s.setAddress(truncate(row.get("address"), 255));
        s.setPostalCode(truncate(row.get("postalCode"), 255));
        s.setCity(truncate(row.get("city"), 255));
        s.setContactPhone(truncate(row.get("phone"), 255));
        Double lat = row.decimal("latitude");
        Double lon = row.decimal("longitude");
        if (lat != null && lon != null && Math.abs(lat) <= 90 && Math.abs(lon) <= 180) {
            s.setLatitude(lat);
            s.setLongitude(lon);
        }
        return s;
    }

    /** Géocodage BAN de l'adresse du site ; best effort (le service peut être indisponible). */
    private boolean geocode(Site site) {
        String query = String.join(" ", nonNull(site.getAddress()), nonNull(site.getPostalCode()), nonNull(site.getCity())).trim();
        if (query.length() < 3) return false;
        try {
            List<GeocodingService.GeocodeResult> results = geocodingService.search(query);
            if (!results.isEmpty() && results.get(0).score() >= MIN_GEOCODE_SCORE) {
                site.setLatitude(results.get(0).latitude());
                site.setLongitude(results.get(0).longitude());
                if (site.getCity() == null) site.setCity(results.get(0).city());
                if (site.getPostalCode() == null) site.setPostalCode(results.get(0).postalCode());
                return true;
            }
        } catch (ResponseStatusException | IllegalArgumentException e) {
            // géocodage indisponible : la commande est importée, le site restera à localiser
        }
        return false;
    }

    private static TourStop.StopType stopType(String v) {
        String n = OrderService.normalize(v);
        if (n.isEmpty() || n.startsWith("livr")) return TourStop.StopType.LIVRAISON;
        if (n.startsWith("enl") || n.startsWith("ramass") || n.startsWith("pick")) return TourStop.StopType.ENLEVEMENT;
        if (n.startsWith("coll") || n.startsWith("prel")) return TourStop.StopType.COLLECTE;
        throw new IllegalArgumentException("type « " + v + " » inconnu (livraison, enlèvement ou collecte)");
    }

    private static Site.SiteKind siteKind(String v) {
        String n = OrderService.normalize(v);
        if (n.startsWith("pharma")) return Site.SiteKind.PHARMACIE;
        if (n.startsWith("labo")) return Site.SiteKind.LABORATOIRE;
        if (n.startsWith("depot")) return Site.SiteKind.DEPOT;
        if (n.startsWith("clinique") || n.startsWith("hopital") || n.startsWith("ehpad") || n.startsWith("etablissement")) {
            return Site.SiteKind.ETABLISSEMENT_SANTE;
        }
        return Site.SiteKind.CLIENT;
    }

    static char detectSeparator(String header) {
        int semi = count(header, ';'), comma = count(header, ','), tab = count(header, '\t');
        if (tab > semi && tab > comma) return '\t';
        return semi >= comma ? ';' : ',';
    }

    private static int count(String s, char c) {
        int n = 0;
        for (char x : s.toCharArray()) if (x == c) n++;
        return n;
    }

    /** Découpage CSV avec guillemets ("a;b" et "" échappé). */
    static List<String> parseLine(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static String nonNull(String v) {
        return v == null ? "" : v;
    }

    private static String truncate(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }

    /** Ligne CSV accessible par nom de champ. */
    private record Row(List<String> cells, Map<String, Integer> col) {
        String get(String field) {
            Integer i = col.get(field);
            if (i == null || i >= cells.size()) return null;
            String v = cells.get(i);
            return v == null || v.isBlank() ? null : v;
        }

        Integer integer(String field) {
            String v = get(field);
            if (v == null) return null;
            try {
                int n = (int) Math.round(Double.parseDouble(v.replace(',', '.')));
                if (n < 0) throw new IllegalArgumentException(field + " négatif");
                return n;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("nombre invalide « " + v + " »");
            }
        }

        Double decimal(String field) {
            String v = get(field);
            if (v == null) return null;
            try {
                return Double.parseDouble(v.replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("nombre invalide « " + v + " »");
            }
        }

        /** Heures « 8:30 », « 08h30 », « 8h » ou « 08:30:00 ». */
        LocalTime time(String field) {
            String v = get(field);
            if (v == null) return null;
            String t = v.toLowerCase().replace('h', ':').trim();
            if (t.endsWith(":")) t = t + "00";
            String[] parts = t.split(":");
            try {
                return LocalTime.of(Integer.parseInt(parts[0].trim()), parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("heure invalide « " + v + " »");
            }
        }
    }
}
