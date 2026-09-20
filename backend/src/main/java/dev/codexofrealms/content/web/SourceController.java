package dev.codexofrealms.content.web;

import dev.codexofrealms.content.application.ingestion.SourceIngestionService;
import dev.codexofrealms.content.application.source.SourceChunkView;
import dev.codexofrealms.content.application.source.SourceContentView;
import dev.codexofrealms.content.application.source.SourceDocumentView;
import dev.codexofrealms.content.application.source.SourceManagementService;
import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import java.io.IOException;
import java.net.URI;
import dev.codexofrealms.content.application.ingestion.SourceSubmission;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    private final SourceIngestionService ingestionService;
    private final SourceManagementService managementService;

    SourceController(
        SourceIngestionService ingestionService,
        SourceManagementService managementService
    ) {
        this.ingestionService = ingestionService;
        this.managementService = managementService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<SourceSubmission> create(
        @CurrentUser AuthenticatedUser user, @PathVariable UUID realmId,
        @RequestHeader("Idempotency-Key") String key,
        @RequestParam("title") String title, @RequestParam("accessPolicyId") UUID policy,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        return response(realmId, ingestionService.create(realmId,user.id(),title,policy,
            file.getBytes(),file.getOriginalFilename(),file.getContentType(),key));
    }
    @PutMapping(path="/{documentId}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<SourceSubmission> replace(
        @CurrentUser AuthenticatedUser user,@PathVariable UUID realmId,@PathVariable UUID documentId,
        @RequestHeader("Idempotency-Key") String key,@RequestParam("accessPolicyId") UUID policy,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        return response(realmId,ingestionService.replace(realmId,documentId,user.id(),policy,
            file.getBytes(),file.getOriginalFilename(),file.getContentType(),key));
    }
    @PostMapping("/{documentId}/reprocess")
    ResponseEntity<SourceSubmission> reprocess(
        @CurrentUser AuthenticatedUser user,@PathVariable UUID realmId,@PathVariable UUID documentId,
        @RequestHeader("Idempotency-Key") String key
    ) {
        return response(realmId,ingestionService.reprocess(realmId,documentId,user.id(),key));
    }
    private ResponseEntity<SourceSubmission> response(UUID realmId,SourceSubmission submission) {
        return ResponseEntity.status(submission.job().noOp() ? 200 : 202)
            .location(URI.create("/api/v1/realms/"+realmId+"/source-jobs/"+submission.job().id())).body(submission);
    }

    @GetMapping
    List<SourceDocumentView> list(@CurrentUser AuthenticatedUser user, @PathVariable UUID realmId) {
        return managementService.list(realmId, user.id());
    }

    @GetMapping("/{documentId}")
    SourceDocumentView get(
        @CurrentUser AuthenticatedUser user, @PathVariable UUID realmId,
        @PathVariable UUID documentId
    ) {
        return managementService.get(realmId, documentId, user.id());
    }

    @GetMapping("/{documentId}/chunks")
    List<SourceChunkView> chunks(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID documentId
    ) {
        return managementService.chunks(realmId, documentId, user.id());
    }

    @GetMapping("/{documentId}/versions/{versionId}/content")
    SourceContentView content(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID documentId,
        @PathVariable UUID versionId
    ) {
        return managementService.content(realmId, documentId, versionId, user.id());
    }

    @DeleteMapping("/{documentId}")
    ResponseEntity<Void> delete(
        @CurrentUser AuthenticatedUser user, @PathVariable UUID realmId,
        @PathVariable UUID documentId
    ) {
        managementService.delete(realmId, documentId, user.id());
        return ResponseEntity.noContent().build();
    }
}
