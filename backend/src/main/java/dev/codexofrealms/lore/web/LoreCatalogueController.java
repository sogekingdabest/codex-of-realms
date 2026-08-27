package dev.codexofrealms.lore.web;

import dev.codexofrealms.lore.application.CreateLoreRelationCommand;
import dev.codexofrealms.lore.application.LoreCatalogueService;
import dev.codexofrealms.lore.application.LoreEntityCommand;
import dev.codexofrealms.lore.application.LoreEntityView;
import dev.codexofrealms.lore.application.LoreRelationView;
import dev.codexofrealms.lore.application.UpdateLoreRelationCommand;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.EntityType;
import dev.codexofrealms.realm.RealmAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/realms/{realmId}/catalogue")
@Tag(name = "Lore catalogue", description = "Manual realm-scoped entities, relations, canon and provenance")
class LoreCatalogueController {

    private final RealmAccess realmAccess;
    private final LoreCatalogueService service;

    LoreCatalogueController(RealmAccess realmAccess, LoreCatalogueService service) {
        this.realmAccess = realmAccess;
        this.service = service;
    }

    @PostMapping("/entities")
    @Operation(summary = "Create a proposed lore entity")
    ResponseEntity<LoreEntityView> createEntity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @RequestBody EntityRequest request
    ) {
        LoreEntityView entity = service.createEntity(
            realmId, currentUser(jwt), request.toCommand()
        );
        return ResponseEntity.created(entityUri(realmId, entity.id())).body(entity);
    }

    @GetMapping("/entities")
    @Operation(summary = "List lore entities visible to the current member")
    List<LoreEntityView> listEntities(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @RequestParam(required = false) EntityType type,
        @RequestParam(required = false) CanonStatus canonStatus
    ) {
        return service.listEntities(realmId, currentUser(jwt), type, canonStatus);
    }

    @GetMapping("/entities/{entityId}")
    @Operation(summary = "Get one visible lore entity")
    LoreEntityView getEntity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID entityId
    ) {
        return service.getEntity(realmId, entityId, currentUser(jwt));
    }

    @PutMapping("/entities/{entityId}")
    @Operation(summary = "Update a lore entity and return it to proposed status")
    LoreEntityView updateEntity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID entityId,
        @RequestBody EntityRequest request
    ) {
        return service.updateEntity(realmId, entityId, currentUser(jwt), request.toCommand());
    }

    @PostMapping("/entities/{entityId}/promotion")
    @Operation(summary = "Promote a proposed lore entity to canon")
    LoreEntityView promoteEntity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID entityId
    ) {
        return service.promoteEntity(realmId, entityId, currentUser(jwt));
    }

    @DeleteMapping("/entities/{entityId}")
    @Operation(summary = "Retire a lore entity without active relations")
    ResponseEntity<Void> deleteEntity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID entityId
    ) {
        service.deleteEntity(realmId, entityId, currentUser(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/relations")
    @Operation(summary = "Create a proposed directional lore relation")
    ResponseEntity<LoreRelationView> createRelation(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @RequestBody RelationRequest request
    ) {
        LoreRelationView relation = service.createRelation(
            realmId, currentUser(jwt), request.toCreateCommand()
        );
        return ResponseEntity.created(relationUri(realmId, relation.id())).body(relation);
    }

    @GetMapping("/relations")
    @Operation(summary = "List relations whose claim and endpoints are visible")
    List<LoreRelationView> listRelations(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(required = false) CanonStatus canonStatus
    ) {
        return service.listRelations(realmId, currentUser(jwt), entityId, canonStatus);
    }

    @GetMapping("/relations/{relationId}")
    @Operation(summary = "Get one relation when its claim and endpoints are visible")
    LoreRelationView getRelation(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID relationId
    ) {
        return service.getRelation(realmId, relationId, currentUser(jwt));
    }

    @PutMapping("/relations/{relationId}")
    @Operation(summary = "Update a relation claim and return it to proposed status")
    LoreRelationView updateRelation(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID relationId,
        @RequestBody RelationUpdateRequest request
    ) {
        return service.updateRelation(
            realmId, relationId, currentUser(jwt), request.toCommand()
        );
    }

    @PostMapping("/relations/{relationId}/promotion")
    @Operation(summary = "Promote a proposed lore relation to canon")
    LoreRelationView promoteRelation(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID relationId
    ) {
        return service.promoteRelation(realmId, relationId, currentUser(jwt));
    }

    @DeleteMapping("/relations/{relationId}")
    @Operation(summary = "Retire a lore relation")
    ResponseEntity<Void> deleteRelation(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID relationId
    ) {
        service.deleteRelation(realmId, relationId, currentUser(jwt));
        return ResponseEntity.noContent().build();
    }

    private UUID currentUser(Jwt jwt) {
        String displayName = firstPresent(
            jwt.getClaimAsString("name"),
            jwt.getClaimAsString("preferred_username"),
            jwt.getSubject()
        );
        return realmAccess.synchronizeIdentity(
            jwt.getClaimAsString("iss"), jwt.getSubject(), displayName,
            jwt.getClaimAsString("email")
        );
    }

    private static URI entityUri(UUID realmId, UUID entityId) {
        return URI.create("/api/v1/realms/" + realmId + "/catalogue/entities/" + entityId);
    }

    private static URI relationUri(UUID realmId, UUID relationId) {
        return URI.create("/api/v1/realms/" + realmId + "/catalogue/relations/" + relationId);
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private record EntityRequest(
        EntityType type,
        String displayName,
        List<String> aliases,
        String description,
        UUID accessPolicyId,
        List<UUID> evidenceChunkIds
    ) {
        LoreEntityCommand toCommand() {
            return new LoreEntityCommand(
                type, displayName, aliases, description, accessPolicyId, evidenceChunkIds
            );
        }
    }

    private record RelationRequest(
        UUID sourceEntityId,
        UUID targetEntityId,
        String relationType,
        String description,
        UUID accessPolicyId,
        List<UUID> evidenceChunkIds
    ) {
        CreateLoreRelationCommand toCreateCommand() {
            return new CreateLoreRelationCommand(
                sourceEntityId, targetEntityId, relationType, description,
                accessPolicyId, evidenceChunkIds
            );
        }
    }

    private record RelationUpdateRequest(
        String relationType,
        String description,
        UUID accessPolicyId,
        List<UUID> evidenceChunkIds
    ) {
        UpdateLoreRelationCommand toCommand() {
            return new UpdateLoreRelationCommand(
                relationType, description, accessPolicyId, evidenceChunkIds
            );
        }
    }
}
