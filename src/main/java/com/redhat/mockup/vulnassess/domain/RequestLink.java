package com.redhat.mockup.vulnassess.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A single link returned by the external links service for a given
 * {@link AssessmentRequest}, once it has moved to {@link RequestStatus#VALIDATING}.
 */
@Entity
@Table(name = "request_link")
public class RequestLink extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    public AssessmentRequest request;

    @Column(name = "url", nullable = false, length = 1024)
    public String url;

    @Column(name = "label", length = 255)
    public String label;

    public RequestLink() {
        // required by Hibernate/Panache
    }

    public RequestLink(AssessmentRequest request, String url, String label) {
        this.request = request;
        this.url = url;
        this.label = label;
    }
}
