package dev.codexofrealms.lore.web;

import dev.codexofrealms.lore.application.LoreSearchService;
import dev.codexofrealms.lore.application.RetrievalResult;
import dev.codexofrealms.realm.RealmAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v1/realms/{realmId}/retrieval")
class LoreRetrievalController {

    private final RealmAccess realmAccess;
    private final LoreSearchService service;

    LoreRetrievalController(RealmAccess realmAccess, LoreSearchService service) {
        this.realmAccess = realmAccess;
        this.service = service;
    }

    @PostMapping
    RetrievalResult retrieve(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @Valid @RequestBody RetrievalRequest request
    ) {
        return service.retrieve(realmId, currentUser(jwt), request.question(), request.limit());
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

    record RetrievalRequest(
        @NotBlank @Size(max = 1000) String question,
        @Min(1) @Max(20) int limit
    ) {
    }
}
