package com.incokalk.repository;

import com.incokalk.model.GroupageMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupageMemberRepository extends JpaRepository<GroupageMember, UUID> {

    List<GroupageMember> findByGroupageIdOrderByCreatedAtAsc(UUID groupageId);

    Optional<GroupageMember> findByGroupageIdAndId(UUID groupageId, UUID id);

    long countByGroupageId(UUID groupageId);

    @Query("SELECT COALESCE(SUM(m.weightKg), 0) FROM GroupageMember m WHERE m.groupageId = :groupageId")
    BigDecimal sumWeightKg(@Param("groupageId") UUID groupageId);

    @Query("SELECT COALESCE(SUM(m.volumeM3), 0) FROM GroupageMember m WHERE m.groupageId = :groupageId")
    BigDecimal sumVolumeM3(@Param("groupageId") UUID groupageId);
}
