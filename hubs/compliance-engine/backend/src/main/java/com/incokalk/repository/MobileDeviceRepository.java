package com.incokalk.repository;

import com.incokalk.model.MobileDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MobileDeviceRepository extends JpaRepository<MobileDevice, UUID> {

    List<MobileDevice> findByUserId(UUID userId);

    List<MobileDevice> findByCompanyId(UUID companyId);

    Optional<MobileDevice> findByDeviceToken(String deviceToken);

    List<MobileDevice> findByUserIdAndIsActiveTrue(UUID userId);

    List<MobileDevice> findByCompanyIdAndIsActiveTrue(UUID companyId);

    int countByCompanyIdAndIsActiveTrue(UUID companyId);

    void deleteByDeviceToken(String deviceToken);
}
