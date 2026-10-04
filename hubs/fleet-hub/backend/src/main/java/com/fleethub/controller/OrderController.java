package com.fleethub.controller;

import com.fleethub.dto.tour.DispatchRequest;
import com.fleethub.dto.tour.DispatchResultDto;
import com.fleethub.dto.tour.OrderDto;
import com.fleethub.dto.tour.OrderImportResultDto;
import com.fleethub.dto.tour.OrderRequest;
import com.fleethub.service.tour.OrderImportService;
import com.fleethub.service.tour.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Planification", description = "Commandes à planifier, import CSV et répartition automatique en tournées")
public class OrderController {

    private final OrderService orderService;
    private final OrderImportService importService;

    @GetMapping
    @Operation(summary = "Commandes d'une journée", description = "À planifier et déjà planifiées (date ISO, défaut : aujourd'hui)")
    public List<OrderDto> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return orderService.list(date != null ? date : LocalDate.now());
    }

    @PostMapping
    @Operation(summary = "Ajouter une commande")
    public OrderDto create(@Valid @RequestBody OrderRequest req) {
        return orderService.create(req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une commande à planifier")
    public void delete(@PathVariable Long id) {
        orderService.delete(id);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importer les commandes (CSV)",
            description = "Colonnes reconnues : reference, code_client, nom, adresse, code_postal, ville, latitude, longitude, "
                    + "type (livraison/enlèvement/collecte), type_site, debut, fin, duree, quantite, telephone, notes, "
                    + "temp_min, temp_max. Sites rapprochés ou créés, adresses géocodées.")
    public OrderImportResultDto importCsv(@RequestPart("file") MultipartFile file,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
            throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide");
        }
        return importService.importCsv(file.getInputStream(), date);
    }

    @PostMapping("/dispatch")
    @Operation(summary = "Répartir automatiquement en tournées",
            description = "Construit une tournée par véhicule en respectant capacités, créneaux et horaires ; "
                    + "renvoie les tournées créées et les commandes non placées avec leur motif")
    public DispatchResultDto dispatch(@Valid @RequestBody DispatchRequest req) {
        return orderService.dispatch(req);
    }
}
