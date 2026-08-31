package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.application.identity.AuthenticatedUser;
import dev.codexofrealms.realm.application.identity.AuthenticatedUserService;
import dev.codexofrealms.realm.application.identity.CurrentUserView;
import dev.codexofrealms.realm.application.lifecycle.RealmLifecycleService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class CurrentUserController {

    private final AuthenticatedUserService userService;
    private final RealmLifecycleService lifecycleService;

    CurrentUserController(
        AuthenticatedUserService userService,
        RealmLifecycleService lifecycleService
    ) {
        this.userService = userService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/me")
    CurrentUserView currentUser(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = userService.synchronize(OidcIdentityMapper.from(jwt));
        return new CurrentUserView(user, lifecycleService.listRealms(user.id()));
    }
}
