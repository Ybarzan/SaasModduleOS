package com.incokalk.repository;

import com.incokalk.model.ReceivingScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReceivingScanRepository extends JpaRepository<ReceivingScan, UUID> {

    List<ReceivingScan> findByReceivingOrderIdOrderByScannedAtAsc(UUID receivingOrderId);

    List<ReceivingScan> findByCompanyIdOrderByScannedAtDesc(UUID companyId);

    List<ReceivingScan> findByItemIdOrderByScannedAtDesc(UUID itemId);
}
