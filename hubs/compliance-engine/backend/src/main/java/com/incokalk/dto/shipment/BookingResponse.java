package com.incokalk.dto.shipment;

import java.math.BigDecimal;
import java.util.Map;

public class BookingResponse {

    private boolean accepted;
    private String carrierReference;
    private String trackingNumber;
    private BigDecimal quotedCost;
    private String currency;
    private String estimatedPickupDate;
    private Integer estimatedTransitDays;
    private String estimatedDeliveryDate;
    private String errorMessage;
    private Map<String, Object> additionalData;
    /** true si cette reponse vient d'un fallback simule, pas d'un vrai appel transporteur. */
    private boolean simulated;

    public BookingResponse() {}

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
    public String getCarrierReference() { return carrierReference; }
    public void setCarrierReference(String carrierReference) { this.carrierReference = carrierReference; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public BigDecimal getQuotedCost() { return quotedCost; }
    public void setQuotedCost(BigDecimal quotedCost) { this.quotedCost = quotedCost; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getEstimatedPickupDate() { return estimatedPickupDate; }
    public void setEstimatedPickupDate(String estimatedPickupDate) { this.estimatedPickupDate = estimatedPickupDate; }
    public Integer getEstimatedTransitDays() { return estimatedTransitDays; }
    public void setEstimatedTransitDays(Integer estimatedTransitDays) { this.estimatedTransitDays = estimatedTransitDays; }
    public String getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public void setEstimatedDeliveryDate(String estimatedDeliveryDate) { this.estimatedDeliveryDate = estimatedDeliveryDate; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Map<String, Object> getAdditionalData() { return additionalData; }
    public void setAdditionalData(Map<String, Object> additionalData) { this.additionalData = additionalData; }
    public boolean isSimulated() { return simulated; }
    public void setSimulated(boolean simulated) { this.simulated = simulated; }
}
