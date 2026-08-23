package com.incokalk.dto.taric;

import java.io.Serializable;
import java.time.LocalDate;

public class TaricMeasureDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private String hsCode;
    private String description;
    private String originCountry;
    private String destinationCountry;
    private double dutyRate;
    private String dutyType;
    private Double specificAmount;
    private String specificUnit;
    private String tradeAgreementCode;
    private boolean isPrefential;
    private String prefentialOriginCriteria;
    private boolean isAntiDumping;
    private Double antiDumpingDuty;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String notes;

    public String getHsCode() { return hsCode; }
    public void setHsCode(String hsCode) { this.hsCode = hsCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOriginCountry() { return originCountry; }
    public void setOriginCountry(String originCountry) { this.originCountry = originCountry; }
    public String getDestinationCountry() { return destinationCountry; }
    public void setDestinationCountry(String destinationCountry) { this.destinationCountry = destinationCountry; }
    public double getDutyRate() { return dutyRate; }
    public void setDutyRate(double dutyRate) { this.dutyRate = dutyRate; }
    public String getDutyType() { return dutyType; }
    public void setDutyType(String dutyType) { this.dutyType = dutyType; }
    public Double getSpecificAmount() { return specificAmount; }
    public void setSpecificAmount(Double specificAmount) { this.specificAmount = specificAmount; }
    public String getSpecificUnit() { return specificUnit; }
    public void setSpecificUnit(String specificUnit) { this.specificUnit = specificUnit; }
    public String getTradeAgreementCode() { return tradeAgreementCode; }
    public void setTradeAgreementCode(String tradeAgreementCode) { this.tradeAgreementCode = tradeAgreementCode; }
    public boolean isPrefential() { return isPrefential; }
    public void setPrefential(boolean prefential) { isPrefential = prefential; }
    public String getPrefentialOriginCriteria() { return prefentialOriginCriteria; }
    public void setPrefentialOriginCriteria(String prefentialOriginCriteria) { this.prefentialOriginCriteria = prefentialOriginCriteria; }
    public boolean isAntiDumping() { return isAntiDumping; }
    public void setAntiDumping(boolean antiDumping) { isAntiDumping = antiDumping; }
    public Double getAntiDumpingDuty() { return antiDumpingDuty; }
    public void setAntiDumpingDuty(Double antiDumpingDuty) { this.antiDumpingDuty = antiDumpingDuty; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
