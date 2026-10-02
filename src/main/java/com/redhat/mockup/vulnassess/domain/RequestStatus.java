package com.redhat.mockup.vulnassess.domain;

/**
 * Lifecycle of an {@link AssessmentRequest}.
 *
 * <p>{@link #DRAFT} is an internal, transient state used while the user is
 * reviewing an upload (files saved, cost estimated) but has not yet decided
 * whether to proceed or cancel. Draft requests are never shown on the home
 * page and are deleted entirely on cancellation.
 */
public enum RequestStatus {

    DRAFT("Draft", "secondary"),
    SUBMITTED("Submitted", "info"),
    IN_PROGRESS("In Progress", "warning"),
    VALIDATING("Validating", "primary"),
    COMPLETED("Completed", "success");

    private final String label;
    private final String badgeClass;

    RequestStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    /** Bootstrap badge color suffix, e.g. "success" for "bg-success". */
    public String getBadgeClass() {
        return badgeClass;
    }
}
