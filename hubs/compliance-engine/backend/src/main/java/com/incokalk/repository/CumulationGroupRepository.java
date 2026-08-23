package com.incokalk.repository;

import com.incokalk.model.CumulationGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CumulationGroupRepository extends JpaRepository<CumulationGroup, UUID> {
    Optional<CumulationGroup> findByCode(String code);
    List<CumulationGroup> findByIsActiveTrue();
}
