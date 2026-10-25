package com.praxio.controller;

import com.praxio.dto.CrossHubDashboardDto;
import com.praxio.dto.DashboardKeysRequest;
import com.praxio.dto.DashboardKeysStatusDto;
import com.praxio.service.CrossHubDashboardService;
import com.praxio.service.DashboardConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CrossHubDashboardService crossHubDashboardService;
    private final DashboardConfigService dashboardConfigService;

    @GetMapping
    public CrossHubDashboardDto getDashboard() {
        return crossHubDashboardService.getDashboard();
    }

    @GetMapping("/keys")
    public DashboardKeysStatusDto getKeysStatus() {
        return dashboardConfigService.getStatus();
    }

    @PutMapping("/keys")
    public DashboardKeysStatusDto updateKeys(@RequestBody DashboardKeysRequest request) {
        return dashboardConfigService.updateKeys(request);
    }
}
