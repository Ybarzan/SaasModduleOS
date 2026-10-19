package com.praxio.dto;

/** Logistix n'a pas de système de facturation propre : toujours NOT_APPLICABLE, jamais de données. */
public record CrossHubBillingDto(
        HubStatus<ComplianceEngineBillingSummaryDto> complianceEngine,
        HubStatus<FleetHubBillingSummaryDto> fleetHub,
        HubStatus<Void> logistix
) {}
