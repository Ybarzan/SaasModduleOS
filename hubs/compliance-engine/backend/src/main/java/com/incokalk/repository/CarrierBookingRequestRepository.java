package com.incokalk.repository;

import com.incokalk.model.CarrierBookingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarrierBookingRequestRepository extends JpaRepository<CarrierBookingRequest, UUID> {

    List<CarrierBookingRequest> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Page<CarrierBookingRequest> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    Optional<CarrierBookingRequest> findByIdAndCompanyId(UUID id, UUID companyId);

    List<CarrierBookingRequest> findByShipmentOrderIdAndCompanyId(UUID shipmentOrderId, UUID companyId);

    List<CarrierBookingRequest> findByCarrierIdAndCompanyId(UUID carrierId, UUID companyId);

    List<CarrierBookingRequest> findByCarrierBookingStatus(CarrierBookingRequest.BookingStatus status);

    @Query("SELECT COUNT(cbr) FROM CarrierBookingRequest cbr WHERE cbr.company.id = :companyId AND cbr.carrierBookingStatus = :status")
    long countByCompanyIdAndStatus(@Param("companyId") UUID companyId, @Param("status") CarrierBookingRequest.BookingStatus status);

    @Query("SELECT COUNT(cbr) FROM CarrierBookingRequest cbr WHERE cbr.company.id = :companyId")
    long countByCompanyId(@Param("companyId") UUID companyId);

    Optional<CarrierBookingRequest> findByCarrierReferenceAndCompanyId(String carrierReference, UUID companyId);
}
