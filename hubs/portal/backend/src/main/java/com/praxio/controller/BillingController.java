package com.praxio.controller;

import com.praxio.dto.CrossHubBillingDto;
import com.praxio.service.CrossHubBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final CrossHubBillingService crossHubBillingService;

    @GetMapping
    public CrossHubBillingDto getBilling() {
        return crossHubBillingService.getBilling();
    }
}
