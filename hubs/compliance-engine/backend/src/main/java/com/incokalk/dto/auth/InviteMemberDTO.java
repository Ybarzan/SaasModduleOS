package com.incokalk.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InviteMemberDTO {
    @NotBlank @Email
    private String email;
    @NotBlank
    private String fullName;
    @NotBlank
    private String role;
}
