package com.incokalk.repository;

import com.incokalk.model.InterBranchTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InterBranchTransferRepository extends JpaRepository<InterBranchTransfer, UUID> {

    List<InterBranchTransfer> findByFromBranchIdOrderByCreatedAtDesc(UUID fromBranchId);

    List<InterBranchTransfer> findByToBranchIdOrderByCreatedAtDesc(UUID toBranchId);

    List<InterBranchTransfer> findByFromBranchIdOrToBranchIdOrderByCreatedAtDesc(UUID fromBranchId, UUID toBranchId);

    List<InterBranchTransfer> findByFromBranchIdInOrToBranchIdInOrderByCreatedAtDesc(
            Collection<UUID> fromBranchIds, Collection<UUID> toBranchIds);
}
