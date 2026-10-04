package com.fleethub.controller;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.tour.SiteDto;
import com.fleethub.dto.tour.SiteRequest;
import com.fleethub.model.Site;
import com.fleethub.repository.SiteRepository;
import com.fleethub.repository.TourRepository;
import com.fleethub.repository.TourStopRepository;
import com.fleethub.security.TenantContext;
import com.fleethub.service.tour.GeocodingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
@Tag(name = "Sites", description = "Lieux desservis par les tournées (clients, pharmacies, laboratoires, dépôts)")
public class SiteController {

    private final SiteRepository siteRepository;
    private final TourStopRepository stopRepository;
    private final TourRepository tourRepository;
    private final GeocodingService geocodingService;

    @GetMapping
    @Operation(summary = "Lister les sites")
    public List<SiteDto> list() {
        return siteRepository.findByCompanyIdOrderByNameAsc(TenantContext.companyId()).stream()
                .map(SiteDto::of).toList();
    }

    @GetMapping("/geocode")
    @Operation(summary = "Géocoder une adresse", description = "Base Adresse Nationale : 5 propositions avec coordonnées GPS")
    public List<GeocodingService.GeocodeResult> geocode(@RequestParam("q") String query) {
        return geocodingService.search(query);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un site")
    public SiteDto get(@PathVariable Long id) {
        return SiteDto.of(load(id));
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Créer un site")
    public SiteDto create(@Valid @RequestBody SiteRequest req) {
        Site site = new Site();
        site.setCompany(TenantContext.require());
        apply(site, req);
        return SiteDto.of(siteRepository.save(site));
    }

    @PutMapping("/{id}")
    @Transactional
    @Operation(summary = "Modifier un site")
    public SiteDto update(@PathVariable Long id, @Valid @RequestBody SiteRequest req) {
        Site site = load(id);
        apply(site, req);
        return SiteDto.of(siteRepository.save(site));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Supprimer un site",
            description = "Refusé si le site figure dans une tournée (désactivez-le plutôt pour conserver la traçabilité)")
    public void delete(@PathVariable Long id) {
        Site site = load(id);
        if (stopRepository.existsBySite(site)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce site figure dans des tournées : désactivez-le pour conserver l'historique");
        }
        tourRepository.detachDepot(site);
        siteRepository.delete(site);
    }

    private void apply(Site site, SiteRequest req) {
        if (req.openingFrom() != null && req.openingTo() != null && req.openingTo().isBefore(req.openingFrom())) {
            throw new IllegalArgumentException("L'heure de fermeture doit suivre l'heure d'ouverture");
        }
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw new IllegalArgumentException("Latitude et longitude doivent être renseignées ensemble");
        }
        site.setName(req.name().trim());
        site.setKind(Site.SiteKind.valueOf(req.kind()));
        site.setReference(req.reference());
        site.setAddress(req.address());
        site.setPostalCode(req.postalCode());
        site.setCity(req.city());
        site.setLatitude(req.latitude());
        site.setLongitude(req.longitude());
        site.setContactName(req.contactName());
        site.setContactPhone(req.contactPhone());
        site.setOpeningFrom(req.openingFrom());
        site.setOpeningTo(req.openingTo());
        site.setServiceMinutes(req.serviceMinutes());
        site.setNotes(req.notes());
        site.setActive(req.active() == null || req.active());
    }

    private Site load(Long id) {
        return siteRepository.findByIdAndCompanyId(id, TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Site introuvable"));
    }
}
