package com.redhat.mockup.vulnassess.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors (the subset of fields we need of) the real ai-scanner
 * {@code ScanStatusDto}, returned by both {@code POST /api/scan/{id}} and
 * {@code GET /api/scan/{id}}. {@code @JsonIgnoreProperties} guards against
 * extra fields the real service may return (e.g. {@code model}, {@code usage},
 * {@code skillPacks}, {@code source}) that we don't need here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScanStatusResponse {

    /** One of: queued, warming, running, succeeded, failed. */
    public String state;

    /** Present when {@code state} is {@code failed}. */
    public String failureReason;

    /** Artifact (file) names; present only when {@code state} is {@code succeeded}. */
    public List<String> artifacts;

    public boolean isSucceeded() {
        return "succeeded".equals(state);
    }

    public boolean isFailed() {
        return "failed".equals(state);
    }
}
