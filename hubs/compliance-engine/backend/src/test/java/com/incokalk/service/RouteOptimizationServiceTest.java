package com.incokalk.service;

import com.incokalk.dto.shipment.RouteOptimizationRequest;
import com.incokalk.dto.shipment.RouteOptimizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RouteOptimizationService — Tests unitaires")
class RouteOptimizationServiceTest {

    private RouteOptimizationService service;

    @BeforeEach
    void setUp() {
        service = new RouteOptimizationService();
    }

    private RouteOptimizationRequest.StopPoint stop(String city, String country, Double lat, Double lon) {
        RouteOptimizationRequest.StopPoint sp = new RouteOptimizationRequest.StopPoint();
        sp.setCity(city);
        sp.setCountry(country);
        sp.setLatitude(lat);
        sp.setLongitude(lon);
        return sp;
    }

    private RouteOptimizationRequest buildRequest(String origin, String dest, List<RouteOptimizationRequest.StopPoint> stops) {
        RouteOptimizationRequest req = new RouteOptimizationRequest();
        req.setOriginCountry(origin);
        req.setDestinationCountry(dest);
        req.setStops(new ArrayList<>(stops));
        return req;
    }

    @Test
    @DisplayName("PARIS → LILLE sans stops → trajet court")
    void optimize_shortTrip_recommendation() {
        RouteOptimizationRequest req = buildRequest("PARIS", "LILLE", List.of());
        RouteOptimizationResult result = service.optimize(req);

        assertThat(result.getRecommendation()).contains("court");
        assertThat(result.getTotalDistanceKm()).isPositive();
        assertThat(result.getEstimatedHours()).isPositive();
        assertThat(result.getEstimatedFuelLiters()).isPositive();
        assertThat(result.getOrderedStops()).hasSize(2);
    }

    @Test
    @DisplayName("PARIS → MADRID avec 1 stop → distance + fuel calculés")
    void optimize_oneStop_computesDistanceAndFuel() {
        RouteOptimizationRequest.StopPoint lyon = stop("LYON", "FR", 45.7640, 4.8357);
        RouteOptimizationRequest req = buildRequest("PARIS", "MADRID", List.of(lyon));
        RouteOptimizationResult result = service.optimize(req);

        assertThat(result.getTotalDistanceKm()).isPositive();
        assertThat(result.getEstimatedFuelLiters()).isPositive();
        assertThat(result.getEstimatedFuelCost()).isPositive();
        assertThat(result.getOrderedStops()).hasSize(3);
        assertThat(result.getOrderedStops().get(1).getCity()).isEqualTo("LYON");
    }

    @Test
    @DisplayName("PARIS → WARSZAWA → trajet long")
    void optimize_longTrip_recommendation() {
        RouteOptimizationRequest req = buildRequest("PARIS", "WARSZAWA", List.of());
        RouteOptimizationResult result = service.optimize(req);

        assertThat(result.getRecommendation()).contains("long");
        assertThat(result.getTotalDistanceKm()).isGreaterThan(1000);
    }

    @Test
    @DisplayName("Consommation et prix du carburant personnalisés")
    void optimize_customFuelPriceAndConsumption() {
        RouteOptimizationRequest req = buildRequest("PARIS", "HAMBURG", List.of());
        req.setFuelPricePerLiter(2.0);
        req.setConsumptionPer100km(25.0);

        RouteOptimizationResult result = service.optimize(req);

        assertThat(result.getEstimatedFuelCost()).isPositive();
        assertThat(result.getEstimatedFuelLiters()).isPositive();
    }

    @Test
    @DisplayName("Sans stops → itinéraire direct (2 stops)")
    void optimize_noStops_directRoute() {
        RouteOptimizationRequest req = buildRequest("PARIS", "MADRID", List.of());
        RouteOptimizationResult result = service.optimize(req);

        assertThat(result.getOrderedStops()).hasSize(2);
        assertThat(result.getTotalStops()).isEqualTo(2);
    }

    @Test
    @DisplayName("Distance, heures et fuel toujours positifs")
    void optimize_allMetricsPositive() {
        RouteOptimizationRequest req = buildRequest("PARIS", "MADRID", List.of());
        RouteOptimizationResult result = service.optimize(req);

        assertThat(result.getTotalDistanceKm()).isPositive();
        assertThat(result.getEstimatedHours()).isPositive();
        assertThat(result.getEstimatedFuelLiters()).isPositive();
        assertThat(result.getEstimatedFuelCost()).isPositive();
        assertThat(result.getEstimatedTollCost()).isPositive();
    }
}
