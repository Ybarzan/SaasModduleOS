package com.incokalk.dto.auth;

import com.incokalk.model.CompanyRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyRoleDTO {
    private UUID userId;
    private UUID companyId;
    private CompanyRole.Role role;
    private String userEmail;
    private String userName;
}