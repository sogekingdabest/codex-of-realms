package dev.codexofrealms.shared;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Canonical factory for the API's RFC Problem Details contract. */
public final class ApiProblemDetails {

    private static final String TYPE_PREFIX = "urn:codex-of-realms:problem:";
    private static final String CODE_PROPERTY = "code";

    private ApiProblemDetails() {
    }

    public static ProblemDetail create(
        HttpStatusCode status,
        String title,
        String detail,
        String code
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setProperty(CODE_PROPERTY, code);
        return problem;
    }
}
