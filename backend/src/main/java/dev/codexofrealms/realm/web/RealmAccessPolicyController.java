package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import dev.codexofrealms.realm.application.access.AccessPolicyService;
import dev.codexofrealms.realm.application.access.AccessPolicyView;
import dev.codexofrealms.realm.application.membership.MembershipView;
import dev.codexofrealms.realm.domain.AccessClassification;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/realms/{realmId}/access-policies")
@Tag(name = "realm-controller")
class RealmAccessPolicyController {

    private final AccessPolicyService accessPolicyService;

    RealmAccessPolicyController(AccessPolicyService accessPolicyService) {
        this.accessPolicyService = accessPolicyService;
    }

    @PostMapping
    ResponseEntity<AccessPolicyView> createPolicy(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @Valid @RequestBody CreateAccessPolicyRequest request
    ) {
        AccessPolicyView policy = accessPolicyService.createAccessPolicy(
            realmId,
            user.id(),
            request.classification(),
            request.name(),
            request.description()
        );
        return ResponseEntity.created(
            RealmWebLocations.childLocation(policy.id())
        ).body(policy);
    }

    @GetMapping("/{policyId}/grants")
    List<MembershipView> listPolicyGrants(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID policyId
    ) {
        return accessPolicyService.listPolicyGrants(realmId, policyId, user.id());
    }

    @GetMapping
    List<AccessPolicyView> listPolicies(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId
    ) {
        return accessPolicyService.listAccessiblePolicies(realmId, user.id());
    }

    @GetMapping("/{policyId}")
    AccessPolicyView getPolicy(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID policyId
    ) {
        return accessPolicyService.getAccessiblePolicy(realmId, policyId, user.id());
    }

    @PutMapping("/{policyId}/grants/{targetUserId}")
    ResponseEntity<Void> grantPolicy(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID policyId,
        @PathVariable UUID targetUserId
    ) {
        accessPolicyService.grantSpoilerAccess(
            realmId,
            policyId,
            user.id(),
            targetUserId
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{policyId}/grants/{targetUserId}")
    ResponseEntity<Void> revokePolicy(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID policyId,
        @PathVariable UUID targetUserId
    ) {
        accessPolicyService.revokeSpoilerAccess(
            realmId,
            policyId,
            user.id(),
            targetUserId
        );
        return ResponseEntity.noContent().build();
    }

    record CreateAccessPolicyRequest(
        @NotNull AccessClassification classification,
        @Size(max = 120) String name,
        @Size(max = 300) String description
    ) {
    }
}
