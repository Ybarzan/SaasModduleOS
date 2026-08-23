package com.fleethub.repository;

import com.fleethub.model.UserTokenCutoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTokenCutoffRepository extends JpaRepository<UserTokenCutoff, Long> {

    Optional<UserTokenCutoff> findByUserId(Long userId);
}
