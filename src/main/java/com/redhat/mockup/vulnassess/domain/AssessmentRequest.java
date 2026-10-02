package com.redhat.mockup.vulnassess.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A code-vulnerability-assessment cost-estimation request.
 *
 * <p>Rows are created in {@link RequestStatus#DRAFT} state as soon as a user
 * uploads files, but only become visible on the home page once confirmed
 * (moved to {@link RequestStatus#SUBMITTED}). Draft rows that are cancelled
 * are deleted outright, together with their uploaded files.
 */
@Entity
@Table(name = "assessment_request")
public class AssessmentRequest extends PanacheEntity {

    /** Short, human-friendly unique identifier shown to users, e.g. "VA-A1B2C3D4". */
    @Column(name = "reference_id", nullable = false, unique = true, length = 32)
    public String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public RequestStatus status;

    @Column(name = "submission_date")
    public LocalDateTime submissionDate;

    @Column(name = "completion_date")
    public LocalDateTime completionDate;

    @Column(name = "estimated_cost", precision = 10, scale = 2)
    public BigDecimal estimatedCost;

    // Eagerly fetched: this mockup only ever deals with a handful of files per
    // request, and the resulting entities are rendered by Qute templates
    // after the owning transaction/session has already closed, so lazy
    // loading would fail there.
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    public List<RequestFile> files = new ArrayList<>();

    // Same eager-fetch rationale as `files` above: rendered by Qute after the
    // owning transaction/session has closed.
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    public List<RequestLink> links = new ArrayList<>();

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy - HH:mm");

    public AssessmentRequest() {
        // required by Hibernate/Panache
    }

    public static String generateReferenceId() {
        return "VA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** Comma-separated list of original file names, for display purposes. */
    public String getFileNamesSummary() {
        return files.stream()
                .map(f -> f.originalFileName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    public int getFileCount() {
        return files.size();
    }

    public String getSubmissionDateFormatted() {
        return submissionDate == null ? "-" : submissionDate.format(DISPLAY_FORMAT);
    }

    public String getCompletionDateFormatted() {
        return completionDate == null ? "-" : completionDate.format(DISPLAY_FORMAT);
    }
}
