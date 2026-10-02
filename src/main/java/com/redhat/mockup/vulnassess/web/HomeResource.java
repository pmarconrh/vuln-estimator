package com.redhat.mockup.vulnassess.web;

import java.util.List;

import com.redhat.mockup.vulnassess.domain.AssessmentRequest;
import com.redhat.mockup.vulnassess.service.AssessmentRequestService;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Home page: lists every successfully submitted assessment request. */
@Path("/")
public class HomeResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance home(List<AssessmentRequest> requests);
    }

    @Inject
    AssessmentRequestService assessmentRequestService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance home() {
        return Templates.home(assessmentRequestService.listVisible());
    }
}
