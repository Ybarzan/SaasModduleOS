package com.praxio.controller;

import com.praxio.dto.SsoCredentialsRequest;
import com.praxio.dto.SsoCredentialsStatusDto;
import com.praxio.dto.SsoLaunchResultDto;
import com.praxio.service.SsoCredentialsService;
import com.praxio.service.SsoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sso")
@RequiredArgsConstructor
public class SsoController {

    private final SsoCredentialsService ssoCredentialsService;
    private final SsoService ssoService;

    @GetMapping("/credentials")
    public SsoCredentialsStatusDto getCredentials() {
        return ssoCredentialsService.getStatus();
    }

    @PutMapping("/credentials")
    public SsoCredentialsStatusDto updateCredentials(@RequestBody SsoCredentialsRequest request) {
        return ssoCredentialsService.updateCredentials(request);
    }

    @PostMapping("/launch/{hub}")
    public SsoLaunchResultDto launch(@PathVariable String hub) {
        return ssoService.launch(hub);
    }
}
