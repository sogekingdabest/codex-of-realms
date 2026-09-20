package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import dev.codexofrealms.realm.application.membership.MembershipService;
import dev.codexofrealms.realm.application.membership.MembershipView;
import dev.codexofrealms.realm.domain.RealmRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/realms/{realmId}/memberships")
@Tag(name = "realm-controller")
class RealmMembershipController {

    private final MembershipService membershipService;

    RealmMembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @PutMapping("/{targetUserId}")
    MembershipView upsertMembership(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID targetUserId,
        @Valid @RequestBody UpsertMembershipRequest request
    ) {
        return membershipService.upsertMembership(
            realmId,
            user.id(),
            targetUserId,
            request.role()
        );
    }

    @GetMapping
    List<MembershipView> listMemberships(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId
    ) {
        return membershipService.listMemberships(realmId, user.id());
    }

    @DeleteMapping("/{targetUserId}")
    ResponseEntity<Void> removeMembership(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID targetUserId
    ) {
        membershipService.removeMembership(realmId, user.id(), targetUserId);
        return ResponseEntity.noContent().build();
    }

    record UpsertMembershipRequest(
        @NotNull RealmRole role
    ) {
    }
}
