package com.incokalk.repository;

import com.incokalk.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    Optional<Subscription> findByCompanyId(UUID companyId);
    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);
    List<Subscription> findByStatus(Subscription.Status status);
    List<Subscription> findByCompanyIdAndStatus(UUID companyId, Subscription.Status status);
}
