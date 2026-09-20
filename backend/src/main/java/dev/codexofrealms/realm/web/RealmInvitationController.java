package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import dev.codexofrealms.realm.application.invitation.InvitationService;
import dev.codexofrealms.realm.application.invitation.InvitationView;
import dev.codexofrealms.realm.domain.RealmRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/realms/{realmId}/invitations")
@Tag(name = "realm-controller")
class RealmInvitationController {

    private final InvitationService invitationService;

    RealmInvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    ResponseEntity<InvitationView> inviteMember(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @Valid @RequestBody InviteMemberRequest request
    ) {
        InvitationView invitation = invitationService.inviteMember(
            realmId,
            user.id(),
            request.email(),
            request.role()
        );
        return ResponseEntity.created(
            RealmWebLocations.childLocation(invitation.id())
        ).body(invitation);
    }

    @GetMapping
    List<InvitationView> listInvitations(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId
    ) {
        return invitationService.listInvitations(realmId, user.id());
    }

    @DeleteMapping("/{invitationId}")
    ResponseEntity<Void> revokeInvitation(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @PathVariable UUID invitationId
    ) {
        invitationService.revokeInvitation(realmId, invitationId, user.id());
        return ResponseEntity.noContent().build();
    }

    record InviteMemberRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull RealmRole role
    ) {
    }
}
