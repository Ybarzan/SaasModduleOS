package com.praxio.repository;

import com.praxio.model.DashboardConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardConfigRepository extends JpaRepository<DashboardConfig, Long> {
}
