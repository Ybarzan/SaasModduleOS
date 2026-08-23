package com.fleethub.repository;

import com.fleethub.model.AdminIpAllowlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminIpAllowlistRepository extends JpaRepository<AdminIpAllowlist, Long> {

    Optional<AdminIpAllowlist> findByIpAddress(String ipAddress);

    boolean existsByIpAddress(String ipAddress);
}
