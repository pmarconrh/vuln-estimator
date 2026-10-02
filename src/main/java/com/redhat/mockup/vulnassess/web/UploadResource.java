package com.redhat.mockup.vulnassess.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.redhat.mockup.vulnassess.domain.AssessmentRequest;
import com.redhat.mockup.vulnassess.service.AssessmentRequestService;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Upload page: lets the user select one or more ZIP files to be assessed. */
@Path("/upload")
public class UploadResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance upload(String error);
    }

    @Inject
    AssessmentRequestService assessmentRequestService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance uploadForm() {
        return Templates.upload(null);
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response submit(@BeanParam UploadForm form) {
        List<FileUpload> files = form.files;

        String validationError = validate(files);
        if (validationError != null) {
            return Response.ok(Templates.upload(validationError))
                    .type(MediaType.TEXT_HTML)
                    .build();
        }

        AssessmentRequest request = assessmentRequestService.createDraft(files);
        return Response.seeOther(URI.create("/orders/" + request.referenceId + "/review")).build();
    }

    private String validate(List<FileUpload> files) {
        if (files == null || files.isEmpty()) {
            return "Please select at least one ZIP file to upload.";
        }
        boolean allZip = files.stream()
                .map(FileUpload::fileName)
                .filter(name -> name != null)
                .allMatch(name -> name.toLowerCase(Locale.ROOT).endsWith(".zip"));
        if (!allZip) {
            return "Only .zip files are accepted. Please remove any non-ZIP files and try again.";
        }
        return null;
    }
}
