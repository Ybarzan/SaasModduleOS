package com.fleethub.service;

import com.fleethub.dto.PackagingRequest;
import com.fleethub.dto.PackagingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Couvre findBestBox() : avant, la liste de boîtes triée par volume décroissant
 * + findFirst() renvoyait toujours la plus grande boîte compatible (ex. une
 * palette de 0,96 m³ pour un seul article de 1 L) -- corrigé en portant le
 * correctif équivalent de compliance-engine (avant le retrait du domaine
 * Transport, voir PR #1 "correctifs P0 de l'audit du 2026-09-29").
 */
@DisplayName("PackagingService — Tests unitaires")
class PackagingServiceTest {

    private PackagingService service;

    @BeforeEach
    void setUp() {
        service = new PackagingService();
    }

    private PackagingRequest.PackagingItem item(double l, double w, double h, double weight, int qty) {
        PackagingRequest.PackagingItem i = new PackagingRequest.PackagingItem();
        i.setSku("A");
        i.setLengthCm(l);
        i.setWidthCm(w);
        i.setHeightCm(h);
        i.setWeightKg(weight);
        i.setQuantity(qty);
        return i;
    }

    private PackagingRequest.AvailableBox box(String ref, double l, double w, double h, double maxKg) {
        PackagingRequest.AvailableBox b = new PackagingRequest.AvailableBox();
        b.setRef(ref);
        b.setLengthCm(l);
        b.setWidthCm(w);
        b.setHeightCm(h);
        b.setMaxWeightKg(maxKg);
        return b;
    }

    @Test
    @DisplayName("Un seul petit article → la plus petite boîte compatible, pas la palette")
    void singleSmallItem_smallestFittingBox() {
        PackagingRequest request = new PackagingRequest();
        request.setItems(List.of(item(10, 10, 10, 1, 1)));
        request.setAvailableBoxes(List.of(
                box("BOX-XS", 30, 20, 15, 5),
                box("BOX-PALLET", 120, 80, 100, 500)
        ));

        PackagingResult result = service.calculatePackaging(request);

        assertThat(result.getTotalBoxes()).isEqualTo(1);
        assertThat(result.getBoxes().get(0).getBoxRef()).isEqualTo("BOX-XS");
    }

    @Test
    @DisplayName("Contenant rempli à moins de 60% → avertissement dans la réponse")
    void underfilledContainer_warns() {
        PackagingRequest request = new PackagingRequest();
        // Volume minuscule dans la seule boîte disponible : forcément < 60% de remplissage.
        request.setItems(List.of(item(5, 5, 5, 1, 1)));
        request.setAvailableBoxes(List.of(box("BOX-PALLET", 120, 80, 100, 500)));

        PackagingResult result = service.calculatePackaging(request);

        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getWarnings().get(0)).contains("BOX-PALLET").contains("surdimensionné");
    }

    @Test
    @DisplayName("Article trop grand pour toute boîte disponible → non emballé")
    void itemTooLarge_unpacked() {
        PackagingRequest request = new PackagingRequest();
        request.setItems(List.of(item(200, 200, 200, 1000, 1)));
        request.setAvailableBoxes(List.of(box("BOX-PALLET", 120, 80, 100, 500)));

        PackagingResult result = service.calculatePackaging(request);

        assertThat(result.getTotalBoxes()).isEqualTo(0);
        assertThat(result.getUnpackedItems()).hasSize(1);
    }
}
