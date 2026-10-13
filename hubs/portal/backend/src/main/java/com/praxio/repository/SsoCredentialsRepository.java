package com.praxio.repository;

import com.praxio.model.SsoCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SsoCredentialsRepository extends JpaRepository<SsoCredentials, Long> {
}
