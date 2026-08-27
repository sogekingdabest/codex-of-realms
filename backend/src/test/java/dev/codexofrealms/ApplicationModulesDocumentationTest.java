package dev.codexofrealms;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.docs.Documenter;

class ApplicationModulesDocumentationTest {

    @Test
    void generatesModuleDiagramsAndCanvases() {
        new Documenter(CodexOfRealmsApplication.class)
            .writeModulesAsPlantUml()
            .writeModuleCanvases();
    }
}
