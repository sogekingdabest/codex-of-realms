package dev.codexofrealms.content.web;

import dev.codexofrealms.content.application.ingestion.*;
import dev.codexofrealms.realm.*;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/realms/{realmId}/source-jobs")
class SourceJobController {
    private final SourceIngestionService service;

    SourceJobController(SourceIngestionService service) {
        this.service = service;
    }

    @GetMapping
    List<SourceJobView> list(@CurrentUser AuthenticatedUser user, @PathVariable UUID realmId) {
        return service.list(realmId, user.id());
    }

    @GetMapping("/{jobId}")
    SourceJobView get(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID realmId,
            @PathVariable UUID jobId) {
        return service.get(realmId, jobId, user.id());
    }

    @PostMapping("/{jobId}/retry")
    SourceJobView retry(
            @CurrentUser AuthenticatedUser user,
            @PathVariable UUID realmId,
            @PathVariable UUID jobId) {
        return service.retry(realmId, jobId, user.id());
    }
}
