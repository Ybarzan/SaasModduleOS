package com.fleethub.repository;

import com.fleethub.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByName(String name);

    Optional<Company> findBySubscriptionId(String subscriptionId);

    List<Company> findAllByOrderByCreatedAtDesc();
}
