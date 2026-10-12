package com.praxio.dto;

/** Jamais les clés elles-mêmes -- seulement si chacune est configurée. */
public record DashboardKeysStatusDto(
        boolean complianceEngineConfigured,
        boolean fleetHubConfigured,
        boolean logistixConfigured
) {}
