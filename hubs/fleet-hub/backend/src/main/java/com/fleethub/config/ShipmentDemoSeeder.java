package com.fleethub.config;

import com.fleethub.model.AppUser;
import com.fleethub.model.Company;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.model.Truck;
import com.fleethub.repository.ShipmentOrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Expéditions de démonstration : des transports routiers en France, réalisés avec
 * les camions de la flotte de démo, à tous les stades (brouillon, réservée, en
 * transit, livrée). Sans elles, la page Expéditions s'ouvrait vide à côté d'un
 * tableau de bord rempli.
 */
final class ShipmentDemoSeeder {

    private ShipmentDemoSeeder() {
    }

    static void seed(Company demo, AppUser owner, Truck[] trucks, ShipmentOrderRepository repository) {
        LocalDateTime now = LocalDateTime.now();
        int n = 0;
        repository.save(order(demo, owner, ++n, ShipmentOrder.Status.DELIVERED, trucks[0],
                "Verallia Chalon", "Chalon-sur-Saône", "71100", "Cave coopérative de Tain", "Tain-l'Hermitage", "26600",
                "Bouteilles en verre, 26 palettes", 18_400, 26, "1180.00", now.minusDays(9), now.minusDays(8)));
        repository.save(order(demo, owner, ++n, ShipmentOrder.Status.DELIVERED, trucks[1],
                "Papeteries de Gascogne", "Mimizan", "40200", "Imprimerie Lescure", "Toulouse", "31200",
                "Bobines de papier kraft", 21_300, 14, "940.00", now.minusDays(6), now.minusDays(5)));
        repository.save(order(demo, owner, ++n, ShipmentOrder.Status.DELIVERED, trucks[2],
                "Conserverie du Lubéron", "Cavaillon", "84300", "Plateforme Sud-Est Distribution", "Saint-Priest", "69800",
                "Conserves de légumes, 33 palettes", 23_900, 33, "765.50", now.minusDays(4), now.minusDays(3)));
        repository.save(order(demo, owner, ++n, ShipmentOrder.Status.IN_TRANSIT, trucks[0],
                "Aciéries de l'Est", "Thionville", "57100", "Chaudronnerie Rhodanienne", "Vénissieux", "69200",
                "Tôles d'acier, 12 colis", 24_500, 12, "1420.00", now.minusHours(7), now.plusHours(5)));
        repository.save(order(demo, owner, ++n, ShipmentOrder.Status.IN_TRANSIT, trucks[2],
                "Minoterie Provençale", "Aix-en-Provence", "13290", "Boulangeries Réunies", "Nice", "06200",
                "Farine en sacs, 24 palettes", 19_200, 24, "610.00", now.minusHours(3), now.plusHours(2)));
        repository.save(order(demo, owner, ++n, ShipmentOrder.Status.BOOKED, trucks[3],
                "Scierie des Landes", "Morcenx", "40110", "Menuiserie Atlantique", "La Rochelle", "17000",
                "Bois de charpente", 17_800, 9, "880.00", now.plusDays(1), now.plusDays(2)));
        repository.save(order(demo, owner, ++n, ShipmentOrder.Status.BOOKED, trucks[1],
                "Brasserie du Nord", "Lille", "59000", "Entrepôt Boissons Île-de-France", "Gennevilliers", "92230",
                "Fûts et caisses, 30 palettes", 22_000, 30, "690.00", now.plusDays(2), now.plusDays(2).plusHours(6)));
        repository.save(order(demo, owner, ++n, ShipmentOrder.Status.DRAFT, null,
                "Fonderie Lyonnaise", "Décines-Charpieu", "69150", "Atelier Mécanique du Jura", "Dole", "39100",
                "Pièces de fonderie, 8 caisses", 6_400, 8, null, now.plusDays(4), now.plusDays(5)));
    }

    private static ShipmentOrder order(Company demo, AppUser owner, int n, ShipmentOrder.Status status, Truck truck,
                                       String shipper, String fromCity, String fromZip,
                                       String consignee, String toCity, String toZip,
                                       String goods, double weightKg, int packages, String cost,
                                       LocalDateTime pickup, LocalDateTime delivery) {
        boolean booked = status != ShipmentOrder.Status.DRAFT;
        boolean shipped = status == ShipmentOrder.Status.IN_TRANSIT || status == ShipmentOrder.Status.DELIVERED;
        boolean delivered = status == ShipmentOrder.Status.DELIVERED;
        BigDecimal amount = cost == null ? null : new BigDecimal(cost);
        return ShipmentOrder.builder()
                .company(demo)
                .user(owner)
                .orderNumber(String.format("DEMO-%04d", n))
                .status(status)
                .truck(truck)
                .shipperName(shipper).shipperCity(fromCity).shipperPostalCode(fromZip).shipperCountry("FR")
                .consigneeName(consignee).consigneeCity(toCity).consigneePostalCode(toZip).consigneeCountry("FR")
                .goodsDescription(goods)
                .weightKg(weightKg)
                .packagesCount(packages)
                .countryOfOrigin("FR")
                .quotedCost(amount)
                .finalCost(delivered ? amount : null)
                .requestedPickupDate(pickup)
                .estimatedDeliveryDate(delivery)
                .actualDeliveryDate(delivered ? delivery : null)
                .bookedAt(booked ? pickup.minusDays(2) : null)
                .shippedAt(shipped ? pickup : null)
                .build();
    }
}
