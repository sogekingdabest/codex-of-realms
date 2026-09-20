package dev.codexofrealms.realm.web;

import java.net.URI;
import java.util.UUID;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

final class RealmWebLocations {

    private RealmWebLocations() {
    }

    static URI childLocation(UUID id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(id)
            .toUri();
    }
}
