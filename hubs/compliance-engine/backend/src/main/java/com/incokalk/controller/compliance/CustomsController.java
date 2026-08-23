package com.incokalk.controller.compliance;

import com.incokalk.model.Company;
import com.incokalk.model.TradeAgreement;
import com.incokalk.security.RequiresPlan;
import com.incokalk.service.CustomsDutyService;
import com.incokalk.service.VatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/v1/customs")
@RequiredArgsConstructor
@Tag(name = "Customs / Douanes", description = "Tarification douanière, TVA, régimes préférentiels")
@RequiresPlan(Company.Plan.STARTER)
public class CustomsController {

    private final CustomsDutyService dutyService;
    private final VatService vatService;

    @GetMapping("/tariff-info")
    @Operation(summary = "Obtenir les informations tarifaires pour un code HS")
    public ResponseEntity<Map<String, Object>> getTariffInfo(
            @RequestParam String hsCode,
            @RequestParam String origin,
            @RequestParam String dest) {
        return ResponseEntity.ok(dutyService.getTariffInfo(hsCode, origin, dest));
    }

    @GetMapping("/duty")
    @Operation(summary = "Calculer les droits de douane détaillés")
    public ResponseEntity<Map<String, Object>> calculateDuty(
            @RequestParam String hsCode,
            @RequestParam String origin,
            @RequestParam String dest,
            @RequestParam double goodsValue,
            @RequestParam(defaultValue = "0") double freight,
            @RequestParam(defaultValue = "0") double insurance) {

        CustomsDutyService.DutyResult result = dutyService.calculateDetailed(
            hsCode, origin, dest, goodsValue, freight, insurance);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dutyAmount", result.dutyAmount());
        response.put("dutyRate", result.dutyRate());
        response.put("dutyType", result.dutyType());
        response.put("isPrefential", result.isPrefential());
        response.put("agreementCode", result.agreementCode());
        response.put("agreementName", result.agreementName());
        response.put("mfnRate", result.mfnRate());
        response.put("savings", result.savings());
        response.put("notes", result.notes());
        response.put("hsCode", hsCode);
        response.put("origin", origin);
        response.put("destination", dest);
        response.put("goodsValue", goodsValue);
        response.put("freight", freight);
        response.put("insurance", insurance);
        response.put("cifValue", goodsValue + freight + insurance);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vat")
    @Operation(summary = "Calculer la TVA (intracom, import, export)")
    public ResponseEntity<Map<String, Object>> calculateVat(
            @RequestParam String origin,
            @RequestParam String dest,
            @RequestParam double goodsValue,
            @RequestParam(defaultValue = "0") double freight,
            @RequestParam(defaultValue = "0") double insurance,
            @RequestParam(defaultValue = "FOB") String incoterm,
            @RequestParam(defaultValue = "true") boolean b2b) {

        VatService.VatResult result = vatService.calculate(
            origin, dest, goodsValue, freight, insurance, incoterm, b2b);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("vatAmount", result.vatAmount());
        response.put("vatRate", result.vatRate());
        response.put("vatType", result.vatType());
        response.put("regime", result.regime());
        response.put("reverseCharge", result.reverseCharge());
        response.put("isExempt", result.isExempt());
        response.put("notes", result.notes());
        response.put("origin", origin);
        response.put("destination", dest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vat-rates")
    @Operation(summary = "Obtenir tous les taux de TVA standard EU")
    public ResponseEntity<Map<String, Double>> getVatRates() {
        return ResponseEntity.ok(vatService.getAllStandardRates());
    }

    @GetMapping("/eu-countries")
    @Operation(summary = "Liste des pays EU")
    public ResponseEntity<Set<String>> getEUCountries() {
        return ResponseEntity.ok(dutyService.getEUCountries());
    }

    @GetMapping("/search")
    @Operation(summary = "Rechercher des codes HS par mot-clé dans les descriptions TARIC")
    public ResponseEntity<Map<String, Object>> searchTariff(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "FR") String dest) {

        return ResponseEntity.ok(dutyService.searchTariff(keyword, dest));
    }

    @GetMapping("/agreements")
    @Operation(summary = "Lister tous les accords commerciaux actifs")
    public ResponseEntity<List<TradeAgreement>> listAgreements(
            @RequestParam(required = false) String country) {
        if (country != null && !country.isBlank()) {
            return ResponseEntity.ok(dutyService.findAgreementsByCountry(country));
        }
        return ResponseEntity.ok(dutyService.findActiveAgreements());
    }

    @GetMapping("/agreement/{code}")
    @Operation(summary = "Obtenir le détail d'un accord commercial par son code")
    public ResponseEntity<?> getAgreementDetail(@PathVariable String code) {
        return dutyService.findAgreementByCode(code)
            .map(a -> ResponseEntity.ok((Object) a))
            .orElse(ResponseEntity.notFound().build());
    }
}
