package dev.codexofrealms.lore.web;

import dev.codexofrealms.lore.LoreSearch;
import dev.codexofrealms.lore.RetrievalResult;
import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/realms/{realmId}/retrieval")
class LoreRetrievalController {

    private final LoreSearch service;

    LoreRetrievalController(LoreSearch service) {
        this.service = service;
    }

    @PostMapping
    RetrievalResult retrieve(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @Valid @RequestBody RetrievalRequest request
    ) {
        return service.retrieve(realmId, user.id(), request.question(), request.limit());
    }

    record RetrievalRequest(
        @NotBlank @Size(max = 1000) String question,
        @Min(1) @Max(20) int limit
    ) {
    }
}
