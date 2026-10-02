package com.redhat.mockup.vulnassess.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import com.redhat.mockup.vulnassess.client.AiScannerClient;
import com.redhat.mockup.vulnassess.client.ScanStatusResponse;
import com.redhat.mockup.vulnassess.client.StartScanForm;
import com.redhat.mockup.vulnassess.domain.AssessmentRequest;
import com.redhat.mockup.vulnassess.domain.RequestLink;
import com.redhat.mockup.vulnassess.domain.RequestStatus;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

/**
 * Two independent background pollers that drive the request lifecycle
 * forward once a user has confirmed an order, by talking to the real
 * external "ai-scanner" service (see {@link AiScannerClient}):
 *
 * <pre>
 * SUBMITTED --(POST /api/scan/{id})--&gt; IN_PROGRESS --(GET /api/scan/{id} succeeded)--&gt; VALIDATING
 * </pre>
 *
 * <p>{@code VALIDATING -&gt; COMPLETED} is deliberately <b>not</b> handled here:
 * per the requirements, only an internal-role user can set a request to
 * {@code COMPLETED}, via the dedicated manual status-change form.
 *
 * <p>Both intervals are configurable (see {@code application.properties}):
 * {@code app.polling.submitted-interval} (duration, e.g. {@code 15s}) and
 * {@code app.polling.in-progress-interval-seconds} (plain number of seconds).
 */
@ApplicationScoped
public class PollingService {

    private static final Logger LOG = Logger.getLogger(PollingService.class);

    @Inject
    AssessmentRequestService assessmentRequestService;

    @Inject
    FileStorageService fileStorageService;

    @RestClient
    AiScannerClient aiScannerClient;

    @ConfigProperty(name = "app.ai-scanner.token")
    String token;

    @ConfigProperty(name = "app.ai-scanner.profile", defaultValue = "security")
    String profile;

    /**
     * Finds every {@code SUBMITTED} request and starts a scan for it via
     * {@code POST /api/scan/{referenceId}}, sending the request's uploaded
     * file(s) as the scan's single source archive (combined into one archive
     * first if there is more than one - the real scanner accepts exactly one
     * archive per scan). On success - or if the scanner reports the scan
     * already exists (409, e.g. a retry after our own earlier call succeeded
     * remotely but the local status update didn't land) - the request moves
     * to {@code IN_PROGRESS}. Any other failure leaves it as {@code SUBMITTED}
     * so it's simply retried on the next poll.
     */
    @Scheduled(every = "{app.polling.submitted-interval}")
    void pollSubmittedRequests() {
        List<AssessmentRequest> submitted = assessmentRequestService.listByStatus(RequestStatus.SUBMITTED);
        if (submitted.isEmpty()) {
            return;
        }
        LOG.debugf("Polling %d SUBMITTED request(s) to start a scan", submitted.size());

        for (AssessmentRequest request : submitted) {
            if (startScan(request)) {
                assessmentRequestService.markInProgress(request.id);
            }
        }
    }

    private boolean startScan(AssessmentRequest request) {
        if (request.files.isEmpty()) {
            LOG.warnf("%s: no uploaded files, nothing to scan - leaving as SUBMITTED", request.referenceId);
            return false;
        }
        try {
            Path archive = fileStorageService.prepareScanArchive(request.referenceId, request.files);
            StartScanForm form = new StartScanForm(profile, archive.toFile());
            Response response = aiScannerClient.startScan(AiScannerClient.scanId(request.referenceId), bearer(), form);
            try {
                int status = response.getStatus();
                if (status == 201 || status == 409) {
                    // 409: a scan with this id already exists - most likely our own
                    // earlier call succeeded remotely; treat it as already started.
                    return true;
                }
                LOG.warnf("%s: ai-scanner returned HTTP %d starting the scan", request.referenceId, status);
                return false;
            } finally {
                response.close();
            }
        } catch (Exception e) {
            LOG.warnf(e, "%s: failed to start scan on ai-scanner (will retry next poll)", request.referenceId);
            return false;
        }
    }

    /**
     * Finds every {@code IN_PROGRESS} request and checks its scan status via
     * {@code GET /api/scan/{referenceId}}. Once the scan has {@code succeeded},
     * the returned artifact names are turned into links - pointing at this
     * app's own download proxy ({@code OrderResource#downloadLink}), not at
     * ai-scanner directly, since its artifact endpoint is Bearer-protected
     * and a user's browser has no way to supply that header - and
     * persisted, and the request moves to {@code VALIDATING}. While still
     * {@code queued}/{@code warming}/{@code running} it is simply retried on
     * the next poll; if it {@code failed}, this is logged and the request is
     * left as {@code IN_PROGRESS} for an internal user to investigate.
     */
    @Scheduled(every = "{app.polling.in-progress-interval-seconds}")
    void pollInProgressRequests() {
        List<AssessmentRequest> inProgress = assessmentRequestService.listByStatus(RequestStatus.IN_PROGRESS);
        if (inProgress.isEmpty()) {
            return;
        }
        LOG.debugf("Polling %d IN_PROGRESS request(s) for scan status", inProgress.size());

        for (AssessmentRequest request : inProgress) {
            checkScanStatus(request);
        }
    }

    private void checkScanStatus(AssessmentRequest request) {
        try {
            ScanStatusResponse status = aiScannerClient.getStatus(AiScannerClient.scanId(request.referenceId), bearer());
            if (status.isSucceeded()) {
                List<RequestLink> links = toLinks(request.referenceId, status.artifacts);
                assessmentRequestService.attachLinksAndMarkValidating(request.id, links);
            } else if (status.isFailed()) {
                LOG.warnf("%s: scan failed on ai-scanner (%s) - left as IN_PROGRESS for review",
                        request.referenceId, status.failureReason);
            }
            // queued / warming / running: nothing to do yet, retried next poll.
        } catch (Exception e) {
            LOG.warnf(e, "%s: failed to fetch scan status from ai-scanner (will retry next poll)",
                    request.referenceId);
        }
    }

    private List<RequestLink> toLinks(String referenceId, List<String> artifactNames) {
        List<RequestLink> links = new ArrayList<>();
        if (artifactNames != null) {
            for (String name : artifactNames) {
                String url = "/orders/" + referenceId + "/links/download/" + name;
                links.add(new RequestLink(null, url, name));
            }
        }
        return links;
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
