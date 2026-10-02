package com.redhat.mockup.vulnassess.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.redhat.mockup.vulnassess.domain.AssessmentRequest;
import com.redhat.mockup.vulnassess.domain.RequestFile;
import com.redhat.mockup.vulnassess.domain.RequestLink;
import com.redhat.mockup.vulnassess.domain.RequestStatus;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

/**
 * Core business logic: turning uploads into draft requests, confirming or
 * cancelling them, listing history for the home page, driving the
 * SUBMITTED/IN_PROGRESS lifecycle transitions used by the background
 * pollers, handling manual (internal-role-only) status changes, and (for
 * demo purposes only) seeding sample history.
 */
@ApplicationScoped
public class AssessmentRequestService {

    private static final Logger LOG = Logger.getLogger(AssessmentRequestService.class);

    private final FileStorageService fileStorageService;
    private final CostEstimationService costEstimationService;

    public AssessmentRequestService(FileStorageService fileStorageService,
            CostEstimationService costEstimationService) {
        this.fileStorageService = fileStorageService;
        this.costEstimationService = costEstimationService;
    }

    /**
     * Persists uploaded files to disk and creates a {@code DRAFT} request row
     * with a randomly generated cost estimate. Nothing is shown on the home
     * page yet: the user must still confirm or cancel via the review page.
     */
    @Transactional
    public AssessmentRequest createDraft(List<FileUpload> uploads) {
        String referenceId = AssessmentRequest.generateReferenceId();

        BigDecimal estimatedCost = costEstimationService.estimate(uploads);
        List<java.nio.file.Path> storedPaths = fileStorageService.store(referenceId, uploads);

        AssessmentRequest request = new AssessmentRequest();
        request.referenceId = referenceId;
        request.status = RequestStatus.DRAFT;
        request.estimatedCost = estimatedCost;
        request.persist();

        for (int i = 0; i < uploads.size(); i++) {
            FileUpload upload = uploads.get(i);
            RequestFile file = new RequestFile(request, upload.fileName(), storedPaths.get(i).toString(),
                    upload.size());
            file.persist();
            request.files.add(file);
        }

        return request;
    }

    public Optional<AssessmentRequest> findByReferenceId(String referenceId) {
        return AssessmentRequest.find("referenceId", referenceId).firstResultOptional();
    }

    /**
     * Confirms a draft request: it becomes {@code SUBMITTED} and appears on
     * the home page. From here, the SUBMITTED-polling background job takes
     * over and will forward it to the assessment intake service.
     */
    @Transactional
    public AssessmentRequest confirm(String referenceId) {
        AssessmentRequest request = findByReferenceId(referenceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request: " + referenceId));
        request.status = RequestStatus.SUBMITTED;
        request.submissionDate = LocalDateTime.now();
        return request;
    }

    /** Cancels a draft request: deletes the DB row and all uploaded files on disk. */
    @Transactional
    public void cancel(String referenceId) {
        AssessmentRequest request = findByReferenceId(referenceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request: " + referenceId));
        request.delete();
        fileStorageService.deleteAll(referenceId);
    }

    /** All requests visible on the home page (everything except DRAFT), most recent first. */
    public List<AssessmentRequest> listVisible() {
        return AssessmentRequest.find(
                "status != ?1 order by submissionDate desc, id desc",
                RequestStatus.DRAFT).list();
    }

    /** All requests currently in the given status, used by the polling jobs. */
    public List<AssessmentRequest> listByStatus(RequestStatus status) {
        return AssessmentRequest.list("status", status);
    }

    /** Called by the SUBMITTED poller once every uploaded file was sent to the intake service. */
    @Transactional
    public void markInProgress(Long requestId) {
        AssessmentRequest request = AssessmentRequest.findById(requestId);
        if (request == null || request.status != RequestStatus.SUBMITTED) {
            return; // already moved on (or gone) - nothing to do
        }
        request.status = RequestStatus.IN_PROGRESS;
        LOG.infof("%s: all files sent to assessment intake service, now IN_PROGRESS", request.referenceId);
    }

    /**
     * Called by the IN_PROGRESS poller once the external links service
     * returned a (possibly empty) list of links for the request.
     */
    @Transactional
    public void attachLinksAndMarkValidating(Long requestId, List<RequestLink> links) {
        AssessmentRequest request = AssessmentRequest.findById(requestId);
        if (request == null || request.status != RequestStatus.IN_PROGRESS) {
            return; // already moved on (or gone) - nothing to do
        }
        // Replace rather than append: makes this idempotent if a request is
        // ever manually moved back to IN_PROGRESS and re-polled (e.g. to
        // regenerate links after a bug fix to how their URLs are built),
        // instead of accumulating duplicate/stale links alongside new ones.
        request.links.clear();
        for (RequestLink link : links) {
            link.request = request;
            link.persist();
            request.links.add(link);
        }
        request.status = RequestStatus.VALIDATING;
        LOG.infof("%s: %d link(s) retrieved, now VALIDATING", request.referenceId, links.size());
    }

    /**
     * Manual status change, only ever invoked from the internal-role-only
     * status-change form/endpoint. Setting {@code COMPLETED} is only
     * reachable through this path - the automated pollers never do it.
     */
    @Transactional
    public AssessmentRequest changeStatusManually(String referenceId, RequestStatus newStatus) {
        AssessmentRequest request = findByReferenceId(referenceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request: " + referenceId));
        request.status = newStatus;
        if (newStatus == RequestStatus.COMPLETED && request.completionDate == null) {
            request.completionDate = LocalDateTime.now();
        }
        LOG.infof("%s: status manually changed to %s", referenceId, newStatus);
        return request;
    }

    // ---------------------------------------------------------------------
    // Demo-only helper: seed sample history so the home page isn't empty.
    // ---------------------------------------------------------------------

    void onStart(@Observes StartupEvent event) {
        seedDemoDataIfEmpty();
    }

    @Transactional
    void seedDemoDataIfEmpty() {
        if (AssessmentRequest.count() > 0) {
            return;
        }
        LOG.info("Seeding demo assessment requests for the home page");

        // Only terminal/closed-off demo history is seeded (COMPLETED, and a
        // VALIDATING example with pre-populated links). SUBMITTED / IN_PROGRESS
        // are intentionally NOT seeded here: those states are meant to be
        // driven by the real upload flow + background pollers, and the seeded
        // rows don't have real files on disk for the pollers to send.
        seedOne(RequestStatus.COMPLETED, LocalDateTime.now().minusDays(9), LocalDateTime.now().minusDays(6),
                new BigDecimal("1240.00"), List.of("payments-service.zip", "shared-libs.zip"), List.of());
        seedOne(RequestStatus.COMPLETED, LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(3),
                new BigDecimal("860.50"), List.of("legacy-billing.zip"), List.of());
        seedOne(RequestStatus.VALIDATING, LocalDateTime.now().minusDays(2), null,
                new BigDecimal("1975.25"), List.of("frontend-app.zip", "api-gateway.zip", "auth-service.zip"),
                List.of(
                        new String[] { "https://example-scan-results.invalid/reports/frontend-app.html",
                                "Frontend app - scan report" },
                        new String[] { "https://example-scan-results.invalid/reports/api-gateway.html",
                                "API gateway - scan report" }));
    }

    private void seedOne(RequestStatus status, LocalDateTime submissionDate, LocalDateTime completionDate,
            BigDecimal cost, List<String> fileNames, List<String[]> demoLinks) {
        AssessmentRequest request = new AssessmentRequest();
        request.referenceId = AssessmentRequest.generateReferenceId();
        request.status = status;
        request.submissionDate = submissionDate;
        request.completionDate = completionDate;
        request.estimatedCost = cost;
        request.persist();

        for (String fileName : fileNames) {
            RequestFile file = new RequestFile(request, fileName,
                    "demo-data/" + request.referenceId + "/" + fileName, 0L);
            file.persist();
            request.files.add(file);
        }

        for (String[] link : demoLinks) {
            RequestLink requestLink = new RequestLink(request, link[0], link[1]);
            requestLink.persist();
            request.links.add(requestLink);
        }
    }
}
