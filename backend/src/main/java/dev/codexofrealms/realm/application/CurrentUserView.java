package dev.codexofrealms.realm.application;

import java.util.List;

public record CurrentUserView(
    AuthenticatedUser user,
    List<RealmSummary> realms
) {
}
