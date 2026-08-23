package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_ip_allowlist")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminIpAllowlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_address", nullable = false, unique = true, length = 45)
    private String ipAddress;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
