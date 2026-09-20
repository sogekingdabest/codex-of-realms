package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.application.access.AccessPolicyException;
import dev.codexofrealms.realm.application.invitation.InvitationException;
import dev.codexofrealms.realm.application.lifecycle.RealmException;
import dev.codexofrealms.realm.application.membership.MembershipException;
import dev.codexofrealms.shared.ApiProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RealmExceptionHandler {

    @ExceptionHandler(RealmException.class)
    ProblemDetail realm(RealmException exception) {
        return ApiProblemDetails.create(
            HttpStatus.NOT_FOUND,
            "Realm unavailable",
            exception.getMessage(),
            exception.code().apiCode()
        );
    }

    @ExceptionHandler(MembershipException.class)
    ProblemDetail membership(MembershipException exception) {
        HttpStatus status = exception.code() == MembershipException.Code.LAST_OWNER
            ? HttpStatus.CONFLICT
            : HttpStatus.NOT_FOUND;
        return ApiProblemDetails.create(
            status,
            status == HttpStatus.CONFLICT ? "Membership conflict" : "Membership unavailable",
            exception.getMessage(),
            exception.code().apiCode()
        );
    }

    @ExceptionHandler(InvitationException.class)
    ProblemDetail invitation(InvitationException exception) {
        HttpStatus status = exception.code() == InvitationException.Code.NOT_FOUND
            ? HttpStatus.NOT_FOUND
            : HttpStatus.CONFLICT;
        return ApiProblemDetails.create(
            status,
            status == HttpStatus.CONFLICT ? "Invitation conflict" : "Invitation unavailable",
            exception.getMessage(),
            exception.code().apiCode()
        );
    }

    @ExceptionHandler(AccessPolicyException.class)
    ProblemDetail accessPolicy(AccessPolicyException exception) {
        HttpStatus status = switch (exception.code()) {
            case UNAVAILABLE, GRANTEE_UNAVAILABLE -> HttpStatus.NOT_FOUND;
            case GRANTS_UNSUPPORTED, INVALID_GRANTEE, NAME_EXHAUSTED -> HttpStatus.CONFLICT;
        };
        return ApiProblemDetails.create(
            status,
            status == HttpStatus.CONFLICT ? "Access policy conflict" : "Access policy unavailable",
            exception.getMessage(),
            exception.code().apiCode()
        );
    }
}
