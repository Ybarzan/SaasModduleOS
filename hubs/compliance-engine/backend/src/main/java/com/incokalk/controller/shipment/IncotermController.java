package com.incokalk.controller.shipment;

import com.incokalk.model.Incoterm;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/incoterms")
public class IncotermController {

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllIncoterms() {
        List<Map<String, Object>> incoterms = Arrays.stream(Incoterm.values())
            .map(incoterm -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", incoterm.name());
                map.put("code", incoterm.name());
                map.put("fullName", incoterm.fullName);
                map.put("mode", incoterm.mode.name().toLowerCase());
                map.put("buyerRiskScore", incoterm.buyerRiskScore);
                map.put("description", getIncotermDescription(incoterm));
                map.put("risks", getIncotermRisks(incoterm));
                map.put("costs", getIncotermCosts(incoterm));
                return map;
            })
            .toList();

        return ResponseEntity.ok(incoterms);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Map<String, Object>> getIncotermByCode(@PathVariable String code) {
        try {
            Incoterm incoterm = Incoterm.valueOf(code.toUpperCase());
            Map<String, Object> map = new HashMap<>();
            map.put("id", incoterm.name());
            map.put("code", incoterm.name());
            map.put("fullName", incoterm.fullName);
            map.put("mode", incoterm.mode.name().toLowerCase());
            map.put("buyerRiskScore", incoterm.buyerRiskScore);
            map.put("description", getIncotermDescription(incoterm));
            map.put("risks", getIncotermRisks(incoterm));
            map.put("costs", getIncotermCosts(incoterm));
            return ResponseEntity.ok(map);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String getIncotermDescription(Incoterm incoterm) {
        return switch (incoterm) {
            case EXW -> "Le vendeur met les marchandises à la disposition de l'acheteur à son établissement.";
            case FCA -> "Le vendeur livre les marchandises au transporteur désigné par l'acheteur.";
            case FAS -> "Le vendeur livre les marchandises le long du navire au port d'embarquement convenu.";
            case FOB -> "Le vendeur livre les marchandises à bord du navire désigné par l'acheteur au port d'embarquement convenu.";
            case CFR -> "Le vendeur livre les marchandises à bord du navire et paie le fret jusqu'au port de destination.";
            case CIF -> "Le vendeur livre les marchandises à bord du navire et paie le fret et l'assurance jusqu'au port de destination.";
            case CPT -> "Le vendeur paie le transport jusqu'au lieu de destination convenu.";
            case CIP -> "Le vendeur paie le transport et l'assurance jusqu'au lieu de destination convenu.";
            case DAP -> "Le vendeur livre les marchandises prêtes pour déchargement au lieu de destination convenu.";
            case DPU -> "Le vendeur livre et décharge les marchandises au lieu de destination convenu.";
            case DDP -> "Le vendeur supporte tous les risques et coûts associés à la livraison des marchandises à destination.";
        };
    }

    private List<String> getIncotermRisks(Incoterm incoterm) {
        return switch (incoterm) {
            case EXW -> Arrays.asList("Tous les risques de transport", "Douanes", "Assurance");
            case FCA -> Arrays.asList("Transport après livraison", "Douanes", "Assurance partielle");
            case FAS -> Arrays.asList("Chargement à bord", "Transport maritime", "Assurance");
            case FOB -> Arrays.asList("Transport maritime", "Chargement", "Assurance");
            case CFR -> Arrays.asList("Transport maritime", "Déchargement");
            case CIF -> Arrays.asList("Transport maritime", "Déchargement");
            case CPT -> Arrays.asList("Transport après livraison", "Déchargement");
            case CIP -> Arrays.asList("Transport après livraison", "Déchargement");
            case DAP -> Arrays.asList("Déchargement", "Risques locaux");
            case DPU -> Arrays.asList("Risques locaux minimes");
            case DDP -> Arrays.asList("Risques minimes pour l'acheteur");
        };
    }

    private List<String> getIncotermCosts(Incoterm incoterm) {
        return switch (incoterm) {
            case EXW -> Arrays.asList("Risques et coûts minimes pour le vendeur");
            case FCA -> Arrays.asList("Transport jusqu'au transporteur", "Assurance partielle");
            case FAS -> Arrays.asList("Transport jusqu'au port", "Assurance maritime partielle");
            case FOB -> Arrays.asList("Transport jusqu'au port", "Assurance maritime");
            case CFR -> Arrays.asList("Transport complet", "Fret maritime");
            case CIF -> Arrays.asList("Transport complet", "Assurance", "Fret maritime");
            case CPT -> Arrays.asList("Transport complet", "Fret terrestre/aérien");
            case CIP -> Arrays.asList("Transport complet", "Assurance", "Fret terrestre/aérien");
            case DAP -> Arrays.asList("Tous les coûts de transport", "Douanes (optionnel)");
            case DPU -> Arrays.asList("Tous les coûts de transport", "Déchargement", "Douanes (optionnel)");
            case DDP -> Arrays.asList("Tous les coûts de transport", "Douanes", "Taxes");
        };
    }
}