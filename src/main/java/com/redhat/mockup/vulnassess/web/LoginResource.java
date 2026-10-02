package com.redhat.mockup.vulnassess.web;

import java.net.URI;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

/**
 * Fake login page. Authentication itself is handled entirely by Quarkus's
 * built-in form-auth mechanism (posts to {@code /j_security_check}); this
 * resource only renders the login form and offers a logout convenience link.
 *
 * <p>Hardcoded demo credentials: <b>demo / demo123</b> (see
 * {@code application.properties}).
 */
@Path("/")
public class LoginResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance login(boolean error);
    }

    @GET
    @Path("/login")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance login(@QueryParam("error") boolean error) {
        return Templates.login(error);
    }

    @GET
    @Path("/logout")
    public Response logout() {
        NewCookie expired = new NewCookie.Builder("vuln-mockup-credential")
                .value("")
                .path("/")
                .maxAge(0)
                .build();
        return Response.seeOther(URI.create("/login")).cookie(expired).build();
    }
}
