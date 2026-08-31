package dev.codexofrealms.qa.web;

import dev.codexofrealms.qa.LoreAnswer;
import dev.codexofrealms.qa.application.answering.QuestionAnsweringService;
import dev.codexofrealms.realm.RealmAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/realms/{realmId}/questions")
class LoreQuestionController {

    private final RealmAccess realmAccess;
    private final QuestionAnsweringService service;

    LoreQuestionController(RealmAccess realmAccess, QuestionAnsweringService service) {
        this.realmAccess = realmAccess;
        this.service = service;
    }

    @PostMapping
    LoreAnswer answer(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @Valid @RequestBody QuestionRequest request
    ) {
        return service.answer(realmId, currentUser(jwt), request.question());
    }

    private UUID currentUser(Jwt jwt) {
        String displayName = firstPresent(jwt.getClaimAsString("name"),
            jwt.getClaimAsString("preferred_username"), jwt.getSubject());
        return realmAccess.synchronizeIdentity(jwt.getClaimAsString("iss"), jwt.getSubject(),
            displayName, jwt.getClaimAsString("email"));
    }

    private static String firstPresent(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    record QuestionRequest(@NotBlank @Size(max = 1000) String question) {
    }
}
