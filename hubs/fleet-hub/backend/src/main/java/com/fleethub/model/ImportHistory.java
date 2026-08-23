package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "import_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "rows_read", nullable = false)
    private int rowsRead;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported;

    @Column(name = "rows_skipped", nullable = false)
    private int rowsSkipped;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;
}
