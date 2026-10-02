package com.redhat.mockup.vulnassess.web;

import java.util.List;

import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import jakarta.ws.rs.core.MediaType;

/** Multipart form backing bean for the ZIP upload request. */
public class UploadForm {

    @RestForm("files")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public List<FileUpload> files;
}
