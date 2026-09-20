package dev.codexofrealms.realm.application.port;

import dev.codexofrealms.realm.application.invitation.InvitationView;
import dev.codexofrealms.realm.domain.RealmRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository {

    boolean hasPendingInvitation(UUID realmId, String email);

    InvitationView createInvitation(
        UUID invitationId,
        UUID realmId,
        String email,
        RealmRole role,
        UUID invitedBy
    );

    Optional<InvitationView> findInvitation(UUID realmId, UUID invitationId);

    List<InvitationView> listInvitations(UUID realmId);

    void acceptInvitation(UUID invitationId, UUID userId);

    boolean revokeInvitation(UUID realmId, UUID invitationId);

    void acceptPendingInvitations(UUID userId, String email);
}
