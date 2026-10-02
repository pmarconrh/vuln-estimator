package com.redhat.mockup.vulnassess.client;

import java.util.Locale;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.MultipartForm;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST client for the real external "ai-scanner" service that performs the
 * actual code vulnerability scan. Confirmed against its source
 * ({@code ScansController}) and a live instance at {@code http://localhost:3000}:
 *
 * <ul>
 *   <li>{@code POST /api/scan/{id}} (multipart/form-data: {@code profile} required,
 *       {@code file} the source archive) - starts a scan under our own chosen id
 *       (our {@code referenceId}). Returns 201 with the scan status, or 409 if a
 *       scan with this id already exists.</li>
 *   <li>{@code GET /api/scan/{id}} - current scan status; once {@code state} is
 *       {@code succeeded}, the response also carries the {@code artifacts}
 *       (file names) produced by the scan.</li>
 * </ul>
 *
 * <p>Every call must carry {@code Authorization: Bearer <token>}
 * (see {@code app.ai-scanner.token} in {@code application.properties}).
 */
@RegisterRestClient(configKey = "ai-scanner")
public interface AiScannerClient {

    @POST
    @Path("/api/scan/{id}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response startScan(@PathParam("id") String id, @HeaderParam("Authorization") String authorization,
            @MultipartForm StartScanForm form);

    @GET
    @Path("/api/scan/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    ScanStatusResponse getStatus(@PathParam("id") String id, @HeaderParam("Authorization") String authorization);

    /**
     * Downloads one artifact of a succeeded scan. Bearer-protected like
     * every other call here - end users' browsers can't supply that header,
     * so this must only ever be invoked server-side, from a proxying
     * endpoint that holds the token (see
     * {@code OrderResource#downloadLink}), never exposed to the browser
     * directly.
     */
    @GET
    @Path("/api/scan/{id}/artifacts/{name}")
    Response downloadArtifact(@PathParam("id") String id, @PathParam("name") String name,
            @HeaderParam("Authorization") String authorization);

    /**
     * The ai-scanner service requires its scan id to match {@code ^[a-z0-9-]{1,64}$}
     * (lowercase letters, digits, dashes only - confirmed against its source,
     * {@code SCAN_ID_PATTERN} in {@code scans.service.ts}). Our own
     * {@code referenceId} (e.g. {@code VA-A1B2C3D4}) is uppercase for
     * human-friendly display, so it is lower-cased for every call to the
     * external service. This is deterministic, so the id used to start a
     * scan always matches the one used to poll its status or download its
     * artifacts.
     */
    static String scanId(String referenceId) {
        return referenceId.toLowerCase(Locale.ROOT);
    }
}
