package dev.codexofrealms.lore.web;

import dev.codexofrealms.lore.application.entity.LoreEntityCommand;
import dev.codexofrealms.lore.application.entity.LoreEntityService;
import dev.codexofrealms.lore.application.entity.LoreEntityView;
import dev.codexofrealms.lore.application.relation.CreateLoreRelationCommand;
import dev.codexofrealms.lore.application.relation.LoreRelationService;
import dev.codexofrealms.lore.application.relation.LoreRelationView;
import dev.codexofrealms.lore.application.relation.UpdateLoreRelationCommand;
import dev.codexofrealms.lore.domain.CanonStatus;
import dev.codexofrealms.lore.domain.EntityType;
import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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

    private final LoreEntityService entities;
    private final LoreRelationService relations;

    LoreCatalogueController(
        LoreEntityService entities,
        LoreRelationService relations
    ) {
        this.entities = entities;
        this.relations = relations;
    }

    @PostMapping("/entities")
    @Operation(summary = "Create a proposed lore entity")
    ResponseEntity<LoreEntityView> createEntity(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @Valid @RequestBody EntityRequest request
    ) {
        LoreEntityView entity = entities.create(
            realmId, user.id(), request.toCommand()
        );
        return ResponseEntity.created(entityUri(realmId, entity.id())).body(entity);
    }

    @GetMapping("/entities")
    @Operation(summary = "List lore entities visible to the current member")
    List<LoreEntityView> listEntities(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @RequestParam(required = false) EntityType type,
        @RequestParam(required = false) CanonStatus canonStatus
    ) {
        return entities.list(realmId, user.id(), type, canonStatus);
    }

    @GetMapping("/entities/{entityId}")
    @Operation(summary = "Get one visible lore entity")
    LoreEntityView getEntity(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID entityId
    ) {
        return entities.get(realmId, entityId, user.id());
    }

    @PutMapping("/entities/{entityId}")
    @Operation(summary = "Update a lore entity and return it to proposed status")
    LoreEntityView updateEntity(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID entityId,
        @Valid @RequestBody EntityRequest request
    ) {
        return entities.update(realmId, entityId, user.id(), request.toCommand());
    }

    @PostMapping("/entities/{entityId}/promotion")
    @Operation(summary = "Promote a proposed lore entity to canon")
    LoreEntityView promoteEntity(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID entityId
    ) {
        return entities.promote(realmId, entityId, user.id());
    }

    @DeleteMapping("/entities/{entityId}")
    @Operation(summary = "Retire a lore entity without active relations")
    ResponseEntity<Void> deleteEntity(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID entityId
    ) {
        entities.delete(realmId, entityId, user.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/relations")
    @Operation(summary = "Create a proposed directional lore relation")
    ResponseEntity<LoreRelationView> createRelation(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @Valid @RequestBody RelationRequest request
    ) {
        LoreRelationView relation = relations.create(
            realmId, user.id(), request.toCreateCommand()
        );
        return ResponseEntity.created(relationUri(realmId, relation.id())).body(relation);
    }

    @GetMapping("/relations")
    @Operation(summary = "List relations whose claim and endpoints are visible")
    List<LoreRelationView> listRelations(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(required = false) CanonStatus canonStatus
    ) {
        return relations.list(realmId, user.id(), entityId, canonStatus);
    }

    @GetMapping("/relations/{relationId}")
    @Operation(summary = "Get one relation when its claim and endpoints are visible")
    LoreRelationView getRelation(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID relationId
    ) {
        return relations.get(realmId, relationId, user.id());
    }

    @PutMapping("/relations/{relationId}")
    @Operation(summary = "Update a relation claim and return it to proposed status")
    LoreRelationView updateRelation(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID relationId,
        @Valid @RequestBody RelationUpdateRequest request
    ) {
        return relations.update(
            realmId, relationId, user.id(), request.toCommand()
        );
    }

    @PostMapping("/relations/{relationId}/promotion")
    @Operation(summary = "Promote a proposed lore relation to canon")
    LoreRelationView promoteRelation(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID relationId
    ) {
        return relations.promote(realmId, relationId, user.id());
    }

    @DeleteMapping("/relations/{relationId}")
    @Operation(summary = "Retire a lore relation")
    ResponseEntity<Void> deleteRelation(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID relationId
    ) {
        relations.delete(realmId, relationId, user.id());
        return ResponseEntity.noContent().build();
    }

    private static URI entityUri(UUID realmId, UUID entityId) {
        return URI.create("/api/v1/realms/" + realmId + "/catalogue/entities/" + entityId);
    }

    private static URI relationUri(UUID realmId, UUID relationId) {
        return URI.create("/api/v1/realms/" + realmId + "/catalogue/relations/" + relationId);
    }

    private record EntityRequest(
        @NotNull EntityType type,
        @NotBlank @Size(max = 160) String displayName,
        @Size(max = 20) List<@NotBlank @Size(max = 120) String> aliases,
        @Size(max = 4000) String description,
        @NotNull UUID accessPolicyId,
        @Size(max = 20) List<@NotNull UUID> evidenceChunkIds
    ) {
        LoreEntityCommand toCommand() {
            return new LoreEntityCommand(
                type, displayName, aliases, description, accessPolicyId, evidenceChunkIds
            );
        }
    }

    private record RelationRequest(
        @NotNull UUID sourceEntityId,
        @NotNull UUID targetEntityId,
        @NotBlank @Size(max = 64) String relationType,
        @Size(max = 2000) String description,
        @NotNull UUID accessPolicyId,
        @Size(max = 20) List<@NotNull UUID> evidenceChunkIds
    ) {
        CreateLoreRelationCommand toCreateCommand() {
            return new CreateLoreRelationCommand(
                sourceEntityId, targetEntityId, relationType, description,
                accessPolicyId, evidenceChunkIds
            );
        }
    }

    private record RelationUpdateRequest(
        @NotBlank @Size(max = 64) String relationType,
        @Size(max = 2000) String description,
        @NotNull UUID accessPolicyId,
        @Size(max = 20) List<@NotNull UUID> evidenceChunkIds
    ) {
        UpdateLoreRelationCommand toCommand() {
            return new UpdateLoreRelationCommand(
                relationType, description, accessPolicyId, evidenceChunkIds
            );
        }
    }
}
