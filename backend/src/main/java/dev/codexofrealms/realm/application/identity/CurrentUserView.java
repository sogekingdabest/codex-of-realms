package dev.codexofrealms.realm.application.identity;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.application.lifecycle.RealmSummary;
import java.util.List;

public record CurrentUserView(
    AuthenticatedUser user,
    List<RealmSummary> realms
) {
}
