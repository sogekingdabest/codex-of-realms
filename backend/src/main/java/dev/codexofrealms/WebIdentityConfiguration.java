package dev.codexofrealms;

import dev.codexofrealms.realm.RealmAccess;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebIdentityConfiguration implements WebMvcConfigurer {

    private final RealmAccess realmAccess;

    WebIdentityConfiguration(RealmAccess realmAccess) {
        this.realmAccess = realmAccess;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver(realmAccess));
    }
}
