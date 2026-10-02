package com.redhat.mockup.vulnassess.web;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import com.redhat.mockup.vulnassess.client.AiScannerClient;
import com.redhat.mockup.vulnassess.domain.AssessmentRequest;
import com.redhat.mockup.vulnassess.domain.RequestStatus;
import com.redhat.mockup.vulnassess.service.AssessmentRequestService;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;

/**
 * Review / confirm / cancel flow for a single (draft) assessment request,
 * the post-confirmation "thank you" page, the request detail page (files,
 * status, links), and the internal-role-only manual status-change form.
 */
@Path("/orders/{referenceId}")
public class OrderResource {

    private static final Logger LOG = Logger.getLogger(OrderResource.class);

    /** Statuses selectable from the manual status-change form (DRAFT is an internal-only transient state). */
    private static final List<RequestStatus> MANUAL_STATUSES = Arrays.stream(RequestStatus.values())
            .filter(status -> status != RequestStatus.DRAFT)
            .toList();

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance review(AssessmentRequest request);

        static native TemplateInstance thankyou(AssessmentRequest request);

        static native TemplateInstance detail(AssessmentRequest request, boolean canManageStatus,
                List<RequestStatus> manualStatuses, String statusError);
    }

    @Inject
    AssessmentRequestService assessmentRequestService;

    @Inject
    SecurityIdentity securityIdentity;

    @RestClient
    AiScannerClient aiScannerClient;

    @ConfigProperty(name = "app.ai-scanner.token")
    String aiScannerToken;

    @GET
    @Path("/review")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance review(@PathParam("referenceId") String referenceId) {
        AssessmentRequest request = load(referenceId);
        return Templates.review(request);
    }

    @POST
    @Path("/confirm")
    public Response confirm(@PathParam("referenceId") String referenceId) {
        load(referenceId);
        // Explicitly moves the request to SUBMITTED; the SUBMITTED-polling
        // background job then takes over and forwards it to the assessment
        // intake service.
        assessmentRequestService.confirm(referenceId);
        return Response.seeOther(URI.create("/orders/" + referenceId + "/thank-you")).build();
    }

    @GET
    @Path("/thank-you")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance thankYou(@PathParam("referenceId") String referenceId) {
        AssessmentRequest request = load(referenceId);
        return Templates.thankyou(request);
    }

    @POST
    @Path("/cancel")
    public Response cancel(@PathParam("referenceId") String referenceId) {
        load(referenceId);
        assessmentRequestService.cancel(referenceId);
        return Response.seeOther(URI.create("/")).build();
    }

    /** Request detail page: files, current status, links (once available). Any authenticated user can view it. */
    @GET
    @Path("")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance detail(@PathParam("referenceId") String referenceId) {
        AssessmentRequest request = load(referenceId);
        return Templates.detail(request, securityIdentity.hasRole("internal"), MANUAL_STATUSES, null);
    }

    /**
     * Streams one artifact of this request's scan back to the browser.
     *
     * <p>ai-scanner's own {@code GET /api/scan/{id}/artifacts/{name}} is
     * Bearer-protected, and a user's browser has no way to supply that
     * header - so this app's own links point here instead (built in
     * {@code PollingService#toLinks}), and this endpoint does the actual
     * call to ai-scanner server-side, with the token, then relays the
     * response. Any authenticated user who can see the request detail page
     * can also download its links.
     */
    @GET
    @Path("/links/download/{name}")
    public Response downloadLink(@PathParam("referenceId") String referenceId, @PathParam("name") String name) {
        AssessmentRequest request = load(referenceId);
        boolean knownArtifact = request.links.stream().anyMatch(link -> name.equals(link.label));
        if (!knownArtifact) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Response upstream = aiScannerClient.downloadArtifact(AiScannerClient.scanId(referenceId),
                name, "Bearer " + aiScannerToken);
        try {
            if (upstream.getStatus() != 200) {
                LOG.warnf("%s: ai-scanner returned HTTP %d downloading artifact %s",
                        referenceId, upstream.getStatus(), name);
                return Response.status(upstream.getStatus()).build();
            }
            byte[] body = upstream.readEntity(byte[].class);
            String contentType = upstream.getHeaderString("Content-Type");
            return Response.ok(body)
                    .header("Content-Type", contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "inline; filename=\"" + name + "\"")
                    .build();
        } catch (Exception e) {
            LOG.warnf(e, "%s: failed to download artifact %s from ai-scanner", referenceId, name);
            return Response.status(Response.Status.BAD_GATEWAY).build();
        } finally {
            upstream.close();
        }
    }

    /**
     * Manual status change. Restricted to the {@code internal} role: this is
     * the *only* way a request can ever be moved to {@code COMPLETED} - the
     * background pollers never do it themselves.
     */
    @POST
    @Path("/status")
    @RolesAllowed("internal")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response changeStatus(@PathParam("referenceId") String referenceId, @RestForm("status") String status) {
        AssessmentRequest request = load(referenceId);
        try {
            RequestStatus newStatus = RequestStatus.valueOf(status);
            assessmentRequestService.changeStatusManually(referenceId, newStatus);
        } catch (IllegalArgumentException e) {
            return Response.ok(Templates.detail(request, true, MANUAL_STATUSES, "Invalid status: " + status))
                    .type(MediaType.TEXT_HTML)
                    .build();
        }
        return Response.seeOther(URI.create("/orders/" + referenceId)).build();
    }

    private AssessmentRequest load(String referenceId) {
        return assessmentRequestService.findByReferenceId(referenceId)
                .orElseThrow(() -> new WebApplicationException("Unknown request: " + referenceId, 404));
    }
}
