package com.incokalk.repository;

import com.incokalk.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<User> findByVerificationToken(String token);
    Optional<User> findByPasswordResetToken(String token);

    @Query("SELECT u.plan FROM User u WHERE u.id = :id")
    Optional<String> findPlanById(UUID id);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.company WHERE u.id = :id")
    Optional<User> findByIdWithCompany(UUID id);

    List<User> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);
}
