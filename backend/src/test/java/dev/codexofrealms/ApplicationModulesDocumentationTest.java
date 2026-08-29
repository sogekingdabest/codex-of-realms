package dev.codexofrealms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.docs.Documenter;

class ApplicationModulesDocumentationTest {

    @Test
    void generatesModuleDiagramsAndCanvases() {
        new Documenter(CodexOfRealmsApplication.class)
            .writeModulesAsPlantUml()
            .writeModuleCanvases();

        assertThat(Path.of("target", "spring-modulith-docs")).isDirectory();
    }
}
