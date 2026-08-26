package dev.codexofrealms.realm.web;

import dev.codexofrealms.realm.application.AuthenticatedUser;
import dev.codexofrealms.realm.application.AuthenticatedUserService;
import dev.codexofrealms.realm.application.CurrentUserView;
import dev.codexofrealms.realm.application.RealmService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class CurrentUserController {

    private final AuthenticatedUserService userService;
    private final RealmService realmService;

    CurrentUserController(
        AuthenticatedUserService userService,
        RealmService realmService
    ) {
        this.userService = userService;
        this.realmService = realmService;
    }

    @GetMapping("/me")
    CurrentUserView currentUser(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = userService.synchronize(OidcIdentityMapper.from(jwt));
        return new CurrentUserView(user, realmService.listRealms(user.id()));
    }
}
