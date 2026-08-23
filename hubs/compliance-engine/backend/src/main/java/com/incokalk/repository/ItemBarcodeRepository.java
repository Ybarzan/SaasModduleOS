package com.incokalk.repository;

import com.incokalk.model.ItemBarcode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemBarcodeRepository extends JpaRepository<ItemBarcode, UUID> {

    Optional<ItemBarcode> findByCompanyIdAndBarcode(UUID companyId, String barcode);

    List<ItemBarcode> findByCompanyIdAndItemId(UUID companyId, UUID itemId);

    List<ItemBarcode> findByCompanyId(UUID companyId);

    long countByCompanyIdAndItemId(UUID companyId, UUID itemId);
}
