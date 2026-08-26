package dev.codexofrealms;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

    @Test
    void verifiesApplicationModuleBoundaries() {
        ApplicationModules.of(CodexOfRealmsApplication.class).verify();
    }
}
