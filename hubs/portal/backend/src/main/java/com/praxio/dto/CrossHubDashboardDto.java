package com.praxio.dto;

public record CrossHubDashboardDto(
        HubStatus<ComplianceEngineSummaryDto> complianceEngine,
        HubStatus<FleetHubSummaryDto> fleetHub,
        HubStatus<LogistixSummaryDto> logistix
) {}
