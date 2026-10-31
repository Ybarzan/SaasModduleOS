package com.incokalk.controller.shipment;

import com.incokalk.dto.compliance.CustomsDutyRequest;
import com.incokalk.dto.compliance.CustomsDutyResult;
import com.incokalk.dto.financial.CargoInsuranceRequest;
import com.incokalk.dto.financial.CargoInsuranceResult;
import com.incokalk.dto.shipment.PackagingRequest;
import com.incokalk.dto.shipment.PackagingResult;
import com.incokalk.dto.shipment.TruckingRateRequest;
import com.incokalk.dto.shipment.TruckingRateResult;
import com.incokalk.service.CargoInsuranceService;
import com.incokalk.service.CurrencyExchangeService;
import com.incokalk.service.CustomsDutyService;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/logistics")
@RequiredArgsConstructor
@Tag(name = "Logistics", description = "Packaging, cubage, tarification et services logistiques")
public class LogisticsController {

    private final FleetHubIntegrationClient fleetHubClient;
    private final CustomsDutyService customsDutyService;
    private final CargoInsuranceService cargoInsuranceService;
    private final CurrencyExchangeService currencyExchangeService;

    @PostMapping("/packaging")
    @Operation(summary = "Calculer le packaging optimal (first-fit decreasing) — via fleet-hub")
    public ResponseEntity<PackagingResult> calculatePackaging(
        @Valid @RequestBody PackagingRequest request) {
        return ResponseEntity.ok(fleetHubClient.calculatePackaging(request));
    }

    @PostMapping("/trucking")
    @Operation(summary = "Obtenir les tarifs LTL/FTL/Express pour une expédition — via fleet-hub")
    public ResponseEntity<TruckingRateResult> calculateTruckingRates(
        @Valid @RequestBody TruckingRateRequest request) {
        return ResponseEntity.ok(fleetHubClient.calculateTruckingRates(request));
    }

    // /v1/logistics/route-optimization a ete retiree avec le domaine Transport :
    // RouteOptimizationService (calcul pur, sans persistance) a ete retire, et le
    // nouveau client fleet-hub ne couvre volontairement que les deux capacites
    // listees ci-dessus (packaging, tarification routiere) -- pas l'optimisation
    // d'itineraire. La page frontend correspondante (RouteOptimizer.tsx) est
    // retiree dans une tache parallele.

    @PostMapping("/customs-duty")
    @Operation(summary = "Calculer les droits de douane import/export")
    public ResponseEntity<CustomsDutyResult> calculateCustomsDuty(
        @Valid @RequestBody CustomsDutyRequest request) {
        double freight = request.getFreightCost() != null ? request.getFreightCost() : 0;
        double insurance = request.getInsuranceCost() != null ? request.getInsuranceCost() : 0;
        double cif = request.getGoodsValue() + freight + insurance;
        CustomsDutyService.DutyResult d = customsDutyService.calculateDetailed(request.getHsCode(),
            request.getOriginCountry(), request.getDestinationCountry(), request.getGoodsValue(), freight, insurance);

        var builder = CustomsDutyResult.builder()
            .hsCode(request.getHsCode())
            .originCountry(request.getOriginCountry().toUpperCase())
            .destinationCountry(request.getDestinationCountry().toUpperCase())
            .cifValue(Math.round(cif * 100.0) / 100.0)
            .rateAvailable(d.rateAvailable())
            .basisType(d.basisType());

        if (!d.rateAvailable()) {
            // Ni taux ni montant : un chiffre crédible et faux est plus dangereux qu'une absence.
            return ResponseEntity.ok(builder.currency(CustomsDutyService.CURRENCY).note(d.notes()).build());
        }

        // "agreement" = accord effectivement appliqué, pas simplement un accord existant pour l'origine.
        builder.dutyRate(Math.round(d.dutyRate() * 10000.0) / 10000.0)
            .agreement(d.isPrefential() ? d.agreementName() : null);

        String currency = request.getCurrency() != null ? request.getCurrency().toUpperCase() : CustomsDutyService.CURRENCY;
        if (!CustomsDutyService.CURRENCY.equals(currency)) {
            double fx = currencyExchangeService.getRate(CustomsDutyService.CURRENCY, currency);
            double converted = currencyExchangeService.convert(d.dutyAmount(), CustomsDutyService.CURRENCY, currency);
            return ResponseEntity.ok(builder
                .dutyAmount(Math.round(converted * 100.0) / 100.0)
                .currency(currency)
                .fxRate(fx)
                .note(d.notes() + " — montant converti de EUR en " + currency + " (taux: " + fx + ")")
                .build());
        }

        return ResponseEntity.ok(builder
            .dutyAmount(d.dutyAmount())
            .currency(CustomsDutyService.CURRENCY)
            .fxRate(1.0)
            .note(d.notes())
            .build());
    }

    @PostMapping("/insurance")
    @Operation(summary = "Calculer la prime d'assurance cargo")
    public ResponseEntity<CargoInsuranceResult> calculateInsurance(
        @Valid @RequestBody CargoInsuranceRequest request) {
        return ResponseEntity.ok(cargoInsuranceService.calculate(request));
    }
}
