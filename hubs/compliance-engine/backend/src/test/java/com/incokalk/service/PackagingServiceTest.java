package com.incokalk.service;

import com.incokalk.dto.shipment.PackagingRequest;
import com.incokalk.dto.shipment.PackagingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PackagingService — Tests unitaires")
class PackagingServiceTest {

    private PackagingService service;

    @BeforeEach
    void setUp() {
        service = new PackagingService();
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private PackagingRequest.PackagingItem itemBuilder(
            String sku, double l, double w, double h, double weight, int qty) {
        PackagingRequest.PackagingItem item = new PackagingRequest.PackagingItem();
        item.setSku(sku);
        item.setLengthCm(l);
        item.setWidthCm(w);
        item.setHeightCm(h);
        item.setWeightKg(weight);
        item.setQuantity(qty);
        return item;
    }

    private PackagingRequest.AvailableBox customBox(String ref, double l, double w, double h, double maxKg) {
        PackagingRequest.AvailableBox box = new PackagingRequest.AvailableBox();
        box.setRef(ref);
        box.setLengthCm(l);
        box.setWidthCm(w);
        box.setHeightCm(h);
        box.setMaxWeightKg(maxKg);
        return box;
    }

    private List<PackagingRequest.AvailableBox> defaultBoxes() {
        return List.of(
                customBox("BOX-XS", 30, 20, 15, 5),
                customBox("BOX-S", 40, 30, 25, 10),
                customBox("BOX-M", 50, 40, 30, 20),
                customBox("BOX-L", 60, 50, 40, 30),
                customBox("BOX-XL", 80, 60, 50, 50),
                customBox("BOX-XXL", 100, 80, 60, 100),
                customBox("BOX-PALLET", 120, 80, 100, 500)
        );
    }

    // ── single item ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Un seul article est placé dans une boîte")
    void calculatePackaging_singleItem_fits() {
        PackagingRequest request = new PackagingRequest();
        request.setItems(List.of(itemBuilder("A", 10, 10, 10, 1, 1)));
        request.setAvailableBoxes(defaultBoxes());

        PackagingResult result = service.calculatePackaging(request);

        assertThat(result.getTotalBoxes()).isEqualTo(1);
        assertThat(result.getUnpackedItems()).isEmpty();
        assertThat(result.getBoxes()).hasSize(1);
        assertThat(result.getBoxes().get(0).getPackedItems()).hasSize(1);
        assertThat(result.getBoxes().get(0).getPackedItems().get(0).getSku()).isEqualTo("A");
    }

    // ── multiple items ──────────────────────────────────────────────────

    @Test
    @DisplayName("Plusieurs articles sont placés dans les boîtes")
    void calculatePackaging_multipleItems_packed() {
        PackagingRequest request = new PackagingRequest();
        request.setItems(List.of(
                itemBuilder("A", 10, 10, 10, 1, 1),
                itemBuilder("B", 20, 20, 20, 3, 1)
        ));
        request.setAvailableBoxes(defaultBoxes());

        PackagingResult result = service.calculatePackaging(request);

        assertThat(result.getTotalBoxes()).isGreaterThanOrEqualTo(1);
        assertThat(result.getUnpackedItems()).isEmpty();
        assertThat(result.getBoxes()).isNotEmpty();
        int totalPacked = result.getBoxes().stream()
                .mapToInt(b -> b.getPackedItems().stream().mapToInt(
                        p -> p.getQuantity()).sum())
                .sum();
        assertThat(totalPacked).isEqualTo(2);
    }

    // ── item too large → unpacked ───────────────────────────────────────

    @Test
    @DisplayName("Article trop grand → non emballé")
    void calculatePackaging_itemTooLarge_unpacked() {
        PackagingRequest request = new PackagingRequest();
        request.setItems(List.of(itemBuilder("BIG", 200, 200, 200, 1000, 1)));
        request.setAvailableBoxes(defaultBoxes());

        PackagingResult result = service.calculatePackaging(request);

        assertThat(result.getTotalBoxes()).isEqualTo(0);
        assertThat(result.getUnpackedItems()).hasSize(1);
        assertThat(result.getUnpackedItems().get(0).getSku()).isEqualTo("BIG");
        assertThat(result.getUnpackedItems().get(0).getReason())
                .contains("Aucune boîte disponible");
    }

    // ── quantity > 1 ────────────────────────────────────────────────────

    @Test
    @DisplayName("Quantité > 1 → articles dupliqués dans la boîte")
    void calculatePackaging_quantityGreaterThanOne_expanded() {
        PackagingRequest request = new PackagingRequest();
        request.setItems(List.of(itemBuilder("A", 10, 10, 10, 1, 3)));
        request.setAvailableBoxes(defaultBoxes());

        PackagingResult result = service.calculatePackaging(request);

        assertThat(result.getTotalBoxes()).isEqualTo(1);
        assertThat(result.getUnpackedItems()).isEmpty();
        PackagingResult.BoxInfo box = result.getBoxes().get(0);
        PackagingResult.PackedItem packed = box.getPackedItems().stream()
                .filter(p -> "A".equals(p.getSku()))
                .findFirst().orElse(null);
        assertThat(packed).isNotNull();
        assertThat(packed.getQuantity()).isEqualTo(3);
    }

    // ── empty items ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Liste d'articles vide → aucun boîte")
    void calculatePackaging_emptyItems_noBoxes() {
        PackagingRequest request = new PackagingRequest();
        request.setItems(new ArrayList<>());
        request.setAvailableBoxes(defaultBoxes());

        PackagingResult result = service.calculatePackaging(request);

        assertThat(result.getTotalBoxes()).isEqualTo(0);
        assertThat(result.getBoxes()).isEmpty();
        assertThat(result.getUnpackedItems()).isEmpty();
        assertThat(result.getTotalVolumeM3()).isEqualTo(0.0);
    }

    // ── custom boxes ────────────────────────────────────────────────────

    @Test
    @DisplayName("Boîtes personnalisées utilisées quand fournies")
    void calculatePackaging_customBoxes_used() {
        PackagingRequest request = new PackagingRequest();
        request.setItems(List.of(itemBuilder("C", 12, 12, 12, 2, 1)));
        request.setAvailableBoxes(List.of(
                customBox("CUSTOM-SMALL", 15, 15, 15, 5)
        ));

        PackagingResult result = service.calculatePackaging(request);

        assertThat(result.getTotalBoxes()).isEqualTo(1);
        assertThat(result.getUnpackedItems()).isEmpty();
        assertThat(result.getBoxes().get(0).getBoxRef()).isEqualTo("CUSTOM-SMALL");
    }
}
