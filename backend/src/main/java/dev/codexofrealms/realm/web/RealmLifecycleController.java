package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import dev.codexofrealms.realm.application.lifecycle.RealmLifecycleService;
import dev.codexofrealms.realm.application.lifecycle.RealmSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/realms")
@Tag(name = "realm-controller")
class RealmLifecycleController {

    private final RealmLifecycleService lifecycleService;

    RealmLifecycleController(RealmLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping
    ResponseEntity<RealmSummary> createRealm(
        @CurrentUser AuthenticatedUser user,
        @Valid @RequestBody CreateRealmRequest request
    ) {
        RealmSummary realm = lifecycleService.createRealm(user.id(), request.name());
        return ResponseEntity.created(RealmWebLocations.childLocation(realm.id())).body(realm);
    }

    @GetMapping
    List<RealmSummary> listRealms(@CurrentUser AuthenticatedUser user) {
        return lifecycleService.listRealms(user.id());
    }

    @GetMapping("/{realmId}")
    RealmSummary getRealm(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId
    ) {
        return lifecycleService.getRealm(realmId, user.id());
    }

    record CreateRealmRequest(
        @NotBlank @Size(max = 120) String name
    ) {
    }
}
