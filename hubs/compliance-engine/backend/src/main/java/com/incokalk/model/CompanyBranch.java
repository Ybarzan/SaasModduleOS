package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_branches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parent_company_id", nullable = false)
    private UUID parentCompanyId;

    @Column(name = "branch_company_id", nullable = false)
    private UUID branchCompanyId;

    @Column(name = "branch_name", nullable = false, length = 200)
    private String branchName;

    @Column(name = "branch_code", length = 50)
    private String branchCode;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "consolidation_enabled")
    @Builder.Default
    private boolean consolidationEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
