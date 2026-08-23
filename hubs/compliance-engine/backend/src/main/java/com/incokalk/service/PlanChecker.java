package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Miroir de RoleChecker pour le palier commercial (voir RequiresPlanAspect). */
@Service
@RequiredArgsConstructor
public class PlanChecker {

    private final CompanyRepository companyRepo;

    public boolean hasMinimumPlan(UUID companyId, Company.Plan required) {
        return companyRepo.findById(companyId)
            .map(c -> c.getPlan().ordinal() >= required.ordinal())
            .orElse(false);
    }
}
