package com.redhat.mockup.vulnassess.client;

import java.io.File;

import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;

import jakarta.ws.rs.core.MediaType;

/**
 * Multipart request body for {@code POST /api/scan/{id}} on the real
 * ai-scanner service. {@code profile} is required by that API (e.g.
 * {@code security}); {@code id} itself travels in the URL path, not the body.
 */
public class StartScanForm {

    @RestForm
    public String profile;

    @RestForm("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public File file;

    public StartScanForm() {
    }

    public StartScanForm(String profile, File file) {
        this.profile = profile;
        this.file = file;
    }
}
