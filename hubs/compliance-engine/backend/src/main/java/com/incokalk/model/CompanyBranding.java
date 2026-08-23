package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "company_branding")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompanyBranding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    @JsonIgnore
    private Company company;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "logo_dark_url", length = 500)
    private String logoDarkUrl;

    @Column(name = "favicon_url", length = 500)
    private String faviconUrl;

    @Column(name = "primary_color", length = 7)
    @Builder.Default
    private String primaryColor = "#2563EB";

    @Column(name = "secondary_color", length = 7)
    @Builder.Default
    private String secondaryColor = "#1E40AF";

    @Column(name = "accent_color", length = 7)
    @Builder.Default
    private String accentColor = "#F59E0B";

    @Column(name = "font_family", length = 100)
    @Builder.Default
    private String fontFamily = "Inter, system-ui, sans-serif";

    @Column(name = "custom_domain", length = 255)
    private String customDomain;

    @Column(name = "ssl_enabled")
    @Builder.Default
    private boolean sslEnabled = true;

    @Column(name = "default_language", length = 5)
    @Builder.Default
    private String defaultLanguage = "FR";

    @Column(name = "supported_languages", length = 100)
    @Builder.Default
    private String supportedLanguages = "FR,EN";

    @Column(name = "portal_title", length = 200)
    private String portalTitle;

    @Column(name = "portal_tagline", length = 500)
    private String portalTagline;

    @Column(name = "footer_text", length = 500)
    private String footerText;

    @Column(name = "custom_css", columnDefinition = "TEXT")
    private String customCss;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
