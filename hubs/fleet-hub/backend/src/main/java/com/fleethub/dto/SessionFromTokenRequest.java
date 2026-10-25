package com.fleethub.dto;

import jakarta.validation.constraints.NotBlank;

public record SessionFromTokenRequest(@NotBlank String token) {}
