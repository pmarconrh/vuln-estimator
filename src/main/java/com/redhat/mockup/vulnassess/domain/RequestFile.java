package com.redhat.mockup.vulnassess.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A single uploaded ZIP file associated with an {@link AssessmentRequest}. */
@Entity
@Table(name = "request_file")
public class RequestFile extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    public AssessmentRequest request;

    @Column(name = "original_file_name", nullable = false)
    public String originalFileName;

    /** Absolute path on disk where the file is stored. */
    @Column(name = "stored_path", nullable = false)
    public String storedPath;

    @Column(name = "size_bytes")
    public long sizeBytes;

    public RequestFile() {
        // required by Hibernate/Panache
    }

    public RequestFile(AssessmentRequest request, String originalFileName, String storedPath, long sizeBytes) {
        this.request = request;
        this.originalFileName = originalFileName;
        this.storedPath = storedPath;
        this.sizeBytes = sizeBytes;
    }
}
