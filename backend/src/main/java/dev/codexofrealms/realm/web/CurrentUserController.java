package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import dev.codexofrealms.realm.application.identity.CurrentUserView;
import dev.codexofrealms.realm.application.lifecycle.RealmLifecycleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class CurrentUserController {

    private final RealmLifecycleService lifecycleService;

    CurrentUserController(RealmLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/me")
    CurrentUserView currentUser(@CurrentUser AuthenticatedUser user) {
        return new CurrentUserView(user, lifecycleService.listRealms(user.id()));
    }
}
