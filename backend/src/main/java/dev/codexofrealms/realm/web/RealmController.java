package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.application.access.AccessPolicyService;
import dev.codexofrealms.realm.application.access.AccessPolicyView;
import dev.codexofrealms.realm.application.identity.AuthenticatedUser;
import dev.codexofrealms.realm.application.identity.AuthenticatedUserService;
import dev.codexofrealms.realm.application.invitation.InvitationService;
import dev.codexofrealms.realm.application.invitation.InvitationView;
import dev.codexofrealms.realm.application.lifecycle.RealmLifecycleService;
import dev.codexofrealms.realm.application.lifecycle.RealmSummary;
import dev.codexofrealms.realm.application.membership.MembershipService;
import dev.codexofrealms.realm.application.membership.MembershipView;
import dev.codexofrealms.realm.domain.AccessClassification;
import dev.codexofrealms.realm.domain.RealmRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/realms")
class RealmController {

    private final AuthenticatedUserService userService;
    private final RealmLifecycleService lifecycleService;
    private final MembershipService membershipService;
    private final InvitationService invitationService;
    private final AccessPolicyService accessPolicyService;

    RealmController(
        AuthenticatedUserService userService,
        RealmLifecycleService lifecycleService,
        MembershipService membershipService,
        InvitationService invitationService,
        AccessPolicyService accessPolicyService
    ) {
        this.userService = userService;
        this.lifecycleService = lifecycleService;
        this.membershipService = membershipService;
        this.invitationService = invitationService;
        this.accessPolicyService = accessPolicyService;
    }

    @PostMapping
    ResponseEntity<RealmSummary> createRealm(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateRealmRequest request
    ) {
        AuthenticatedUser user = currentUser(jwt);
        RealmSummary realm = lifecycleService.createRealm(user.id(), request.name());

        return ResponseEntity
            .created(childLocation(realm.id()))
            .body(realm);
    }

    @GetMapping
    List<RealmSummary> listRealms(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = currentUser(jwt);
        return lifecycleService.listRealms(user.id());
    }

    @GetMapping("/{realmId}")
    RealmSummary getRealm(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId
    ) {
        AuthenticatedUser user = currentUser(jwt);
        return lifecycleService.getRealm(realmId, user.id());
    }

    @PutMapping("/{realmId}/memberships/{targetUserId}")
    MembershipView upsertMembership(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID targetUserId,
        @Valid @RequestBody UpsertMembershipRequest request
    ) {
        AuthenticatedUser user = currentUser(jwt);
        return membershipService.upsertMembership(
            realmId,
            user.id(),
            targetUserId,
            request.role()
        );
    }

    @GetMapping("/{realmId}/memberships")
    List<MembershipView> listMemberships(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId
    ) {
        AuthenticatedUser user = currentUser(jwt);
        return membershipService.listMemberships(realmId, user.id());
    }

    @PostMapping("/{realmId}/invitations")
    ResponseEntity<InvitationView> inviteMember(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @Valid @RequestBody InviteMemberRequest request
    ) {
        AuthenticatedUser user = currentUser(jwt);
        InvitationView invitation = invitationService.inviteMember(
            realmId, user.id(), request.email(), request.role()
        );
        return ResponseEntity.created(childLocation(invitation.id())).body(invitation);
    }

    @GetMapping("/{realmId}/invitations")
    List<InvitationView> listInvitations(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId
    ) {
        AuthenticatedUser user = currentUser(jwt);
        return invitationService.listInvitations(realmId, user.id());
    }

    @DeleteMapping("/{realmId}/invitations/{invitationId}")
    ResponseEntity<Void> revokeInvitation(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID invitationId
    ) {
        AuthenticatedUser user = currentUser(jwt);
        invitationService.revokeInvitation(realmId, invitationId, user.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{realmId}/memberships/{targetUserId}")
    ResponseEntity<Void> removeMembership(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID targetUserId
    ) {
        AuthenticatedUser user = currentUser(jwt);
        membershipService.removeMembership(realmId, user.id(), targetUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{realmId}/access-policies")
    ResponseEntity<AccessPolicyView> createPolicy(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @Valid @RequestBody CreateAccessPolicyRequest request
    ) {
        AuthenticatedUser user = currentUser(jwt);
        AccessPolicyView policy = accessPolicyService.createAccessPolicy(
            realmId,
            user.id(),
            request.classification(),
            request.name(),
            request.description()
        );

        return ResponseEntity
            .created(childLocation(policy.id()))
            .body(policy);
    }

    @GetMapping("/{realmId}/access-policies/{policyId}/grants")
    List<MembershipView> listPolicyGrants(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID policyId
    ) {
        AuthenticatedUser user = currentUser(jwt);
        return accessPolicyService.listPolicyGrants(realmId, policyId, user.id());
    }

    @GetMapping("/{realmId}/access-policies")
    List<AccessPolicyView> listPolicies(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId
    ) {
        AuthenticatedUser user = currentUser(jwt);
        return accessPolicyService.listAccessiblePolicies(realmId, user.id());
    }

    @GetMapping("/{realmId}/access-policies/{policyId}")
    AccessPolicyView getPolicy(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID policyId
    ) {
        AuthenticatedUser user = currentUser(jwt);
        return accessPolicyService.getAccessiblePolicy(realmId, policyId, user.id());
    }

    @PutMapping("/{realmId}/access-policies/{policyId}/grants/{targetUserId}")
    ResponseEntity<Void> grantPolicy(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID policyId,
        @PathVariable UUID targetUserId
    ) {
        AuthenticatedUser user = currentUser(jwt);
        accessPolicyService.grantSpoilerAccess(
            realmId,
            policyId,
            user.id(),
            targetUserId
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{realmId}/access-policies/{policyId}/grants/{targetUserId}")
    ResponseEntity<Void> revokePolicy(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID realmId,
        @PathVariable UUID policyId,
        @PathVariable UUID targetUserId
    ) {
        AuthenticatedUser user = currentUser(jwt);
        accessPolicyService.revokeSpoilerAccess(
            realmId,
            policyId,
            user.id(),
            targetUserId
        );
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser currentUser(Jwt jwt) {
        return userService.synchronize(OidcIdentityMapper.from(jwt));
    }

    private static java.net.URI childLocation(UUID id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(id)
            .toUri();
    }

    record CreateRealmRequest(
        @NotBlank @Size(max = 120) String name
    ) {
    }

    record UpsertMembershipRequest(
        @NotNull RealmRole role
    ) {
    }

    record InviteMemberRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull RealmRole role
    ) {
    }

    record CreateAccessPolicyRequest(
        @NotNull AccessClassification classification,
        @Size(max = 120) String name,
        @Size(max = 300) String description
    ) {
    }
}
