package dev.codexofrealms.content.web;

import dev.codexofrealms.content.application.SourceDocumentView;
import dev.codexofrealms.content.application.SourceIngestionService;
import dev.codexofrealms.realm.RealmAccess;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/realms/{realmId}/sources")
class SourceController {

    private final RealmAccess realmAccess;
    private final SourceIngestionService service;

    SourceController(RealmAccess realmAccess, SourceIngestionService service) {
        this.realmAccess = realmAccess;
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<SourceDocumentView> create(
        @AuthenticationPrincipal Jwt jwt, @PathVariable UUID realmId,
        @RequestParam("title") String title,
        @RequestParam("accessPolicyId") UUID accessPolicyId,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        SourceDocumentView source = service.create(realmId, currentUser(jwt), title,
            accessPolicyId, file.getBytes(), file.getOriginalFilename(), file.getContentType());
        return ResponseEntity.created(URI.create("/api/v1/realms/" + realmId + "/sources/" + source.id())).body(source);
    }

    @PutMapping(path = "/{documentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    SourceDocumentView replace(
        @AuthenticationPrincipal Jwt jwt, @PathVariable UUID realmId,
        @PathVariable UUID documentId,
        @RequestParam("accessPolicyId") UUID accessPolicyId,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        return service.replace(realmId, documentId, currentUser(jwt), accessPolicyId,
            file.getBytes(), file.getOriginalFilename(), file.getContentType());
    }

    @PostMapping("/{documentId}/reprocess")
    SourceDocumentView reprocess(
        @AuthenticationPrincipal Jwt jwt, @PathVariable UUID realmId,
        @PathVariable UUID documentId
    ) {
        return service.reprocess(realmId, documentId, currentUser(jwt));
    }

    @GetMapping
    List<SourceDocumentView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID realmId) {
        return service.list(realmId, currentUser(jwt));
    }

    @GetMapping("/{documentId}")
    SourceDocumentView get(
        @AuthenticationPrincipal Jwt jwt, @PathVariable UUID realmId,
        @PathVariable UUID documentId
    ) {
        return service.get(realmId, documentId, currentUser(jwt));
    }

    @DeleteMapping("/{documentId}")
    ResponseEntity<Void> delete(
        @AuthenticationPrincipal Jwt jwt, @PathVariable UUID realmId,
        @PathVariable UUID documentId
    ) {
        service.delete(realmId, documentId, currentUser(jwt));
        return ResponseEntity.noContent().build();
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
}
