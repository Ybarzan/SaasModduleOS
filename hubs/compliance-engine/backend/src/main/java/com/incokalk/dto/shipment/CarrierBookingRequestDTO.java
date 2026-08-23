package com.incokalk.dto.shipment;

import com.incokalk.model.ShipmentOrder.ContainerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class CarrierBookingRequestDTO {

    private UUID id;

    @NotNull
    private UUID carrierId;

    @NotNull
    private UUID shipmentOrderId;

    @NotBlank
    private String carrierName;

    @NotBlank
    private String carrierCode;

    @NotBlank
    private String carrierApiEndpoint;

    private String carrierApiKey;

    @NotBlank
    private String originCountry;

    @NotBlank
    private String destinationCountry;

    private ContainerType containerType;

    @NotNull
    private Integer quantity;

    private Boolean isReefer;

    private Double containerLengthM;

    private Double containerWidthM;

    private Double containerHeightM;

    private Integer estimatedWeightKg;

    private Double estimatedVolumeM3;

    private String goodsDescription;

    private LocalDateTime bookingDate;

    private LocalDateTime pickupDate;

    private LocalDateTime deliveryDate;

    private String bookingStatus;

    private String serviceType;

    private String specialInstructions;

    private LocalDate requestedPickupDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getCarrierId() { return carrierId; }
    public void setCarrierId(UUID carrierId) { this.carrierId = carrierId; }
    public UUID getShipmentOrderId() { return shipmentOrderId; }
    public void setShipmentOrderId(UUID shipmentOrderId) { this.shipmentOrderId = shipmentOrderId; }
    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
    public String getCarrierCode() { return carrierCode; }
    public void setCarrierCode(String carrierCode) { this.carrierCode = carrierCode; }
    public String getCarrierApiEndpoint() { return carrierApiEndpoint; }
    public void setCarrierApiEndpoint(String carrierApiEndpoint) { this.carrierApiEndpoint = carrierApiEndpoint; }
    public String getCarrierApiKey() { return carrierApiKey; }
    public void setCarrierApiKey(String carrierApiKey) { this.carrierApiKey = carrierApiKey; }
    public String getOriginCountry() { return originCountry; }
    public void setOriginCountry(String originCountry) { this.originCountry = originCountry; }
    public String getDestinationCountry() { return destinationCountry; }
    public void setDestinationCountry(String destinationCountry) { this.destinationCountry = destinationCountry; }
    public ContainerType getContainerType() { return containerType; }
    public void setContainerType(ContainerType containerType) { this.containerType = containerType; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Boolean getIsReefer() { return isReefer; }
    public void setIsReefer(Boolean isReefer) { this.isReefer = isReefer; }
    public Double getContainerLengthM() { return containerLengthM; }
    public void setContainerLengthM(Double containerLengthM) { this.containerLengthM = containerLengthM; }
    public Double getContainerWidthM() { return containerWidthM; }
    public void setContainerWidthM(Double containerWidthM) { this.containerWidthM = containerWidthM; }
    public Double getContainerHeightM() { return containerHeightM; }
    public void setContainerHeightM(Double containerHeightM) { this.containerHeightM = containerHeightM; }
    public Integer getEstimatedWeightKg() { return estimatedWeightKg; }
    public void setEstimatedWeightKg(Integer estimatedWeightKg) { this.estimatedWeightKg = estimatedWeightKg; }
    public Double getEstimatedVolumeM3() { return estimatedVolumeM3; }
    public void setEstimatedVolumeM3(Double estimatedVolumeM3) { this.estimatedVolumeM3 = estimatedVolumeM3; }
    public String getGoodsDescription() { return goodsDescription; }
    public void setGoodsDescription(String goodsDescription) { this.goodsDescription = goodsDescription; }
    public LocalDateTime getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDateTime bookingDate) { this.bookingDate = bookingDate; }
    public LocalDateTime getPickupDate() { return pickupDate; }
    public void setPickupDate(LocalDateTime pickupDate) { this.pickupDate = pickupDate; }
    public LocalDateTime getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDateTime deliveryDate) { this.deliveryDate = deliveryDate; }
    public String getBookingStatus() { return bookingStatus; }
    public void setBookingStatus(String bookingStatus) { this.bookingStatus = bookingStatus; }
    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }
    public String getSpecialInstructions() { return specialInstructions; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
    public LocalDate getRequestedPickupDate() { return requestedPickupDate; }
    public void setRequestedPickupDate(LocalDate requestedPickupDate) { this.requestedPickupDate = requestedPickupDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}