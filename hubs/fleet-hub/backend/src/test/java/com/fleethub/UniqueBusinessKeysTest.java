package com.fleethub;

import com.fleethub.model.Company;
import com.fleethub.model.Driver;
import com.fleethub.model.Truck;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.TruckRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V26 : l'immatriculation et le numéro de permis sont uniques PAR SOCIÉTÉ, au niveau
 * de la base (la vérification applicative seule laissait passer deux créations
 * simultanées).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UniqueBusinessKeysTest {

    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private TruckRepository truckRepository;
    @Autowired
    private DriverRepository driverRepository;

    private Company company(String name) {
        Company c = new Company();
        c.setName(name + "-" + System.nanoTime());
        c.setPlan(Company.SubscriptionPlan.PRO);
        c.setStatus(Company.CompanyStatus.ACTIVE);
        c.setCreatedAt(LocalDateTime.now());
        c.setAccessCode(Company.generateAccessCode());
        return companyRepository.save(c);
    }

    private Truck truck(Company c, String registration) {
        Truck t = new Truck();
        t.setCompany(c);
        t.setRegistration(registration);
        t.setBrand("Renault");
        t.setModel("Master");
        t.setTruckType(Truck.TruckType.VUL);
        t.setFuelType(Truck.FuelType.DIESEL);
        return t;
    }

    private Driver driver(Company c, String license) {
        Driver d = new Driver();
        d.setCompany(c);
        d.setFirstName("Jean");
        d.setLastName("Dupont");
        d.setLicenseNumber(license);
        d.setPhone("0600000000");
        return d;
    }

    @Test
    void sameRegistration_isAllowedInTwoCompanies_butNotTwiceInOne() {
        Company a = company("A");
        Company b = company("B");
        truckRepository.saveAndFlush(truck(a, "UQ-001-AA"));
        assertDoesNotThrow(() -> truckRepository.saveAndFlush(truck(b, "UQ-001-AA")));
        assertThrows(DataIntegrityViolationException.class,
                () -> truckRepository.saveAndFlush(truck(a, "UQ-001-AA")));
    }

    @Test
    void sameLicense_isAllowedInTwoCompanies_butNotTwiceInOne() {
        Company a = company("A");
        Company b = company("B");
        driverRepository.saveAndFlush(driver(a, "PERMIS-1"));
        assertDoesNotThrow(() -> driverRepository.saveAndFlush(driver(b, "PERMIS-1")));
        assertThrows(DataIntegrityViolationException.class,
                () -> driverRepository.saveAndFlush(driver(a, "PERMIS-1")));
    }
}
