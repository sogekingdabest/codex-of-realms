package dev.codexofrealms;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class InternalArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("dev.codexofrealms");

    @Test
    void applicationDoesNotDependOnAdapters() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..adapter..")
            .because("application owns its output ports and adapters implement them")
            .check(PRODUCTION_CLASSES);
    }

    @Test
    void domainDoesNotDependOnOuterLayers() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..adapter..", "..web..")
            .because("domain rules must stay independent from orchestration and technology")
            .check(PRODUCTION_CLASSES);
    }

    @Test
    void publicModuleApisDoNotDependOnAdapters() {
        noClasses()
            .that().resideInAnyPackage(
                "dev.codexofrealms.realm",
                "dev.codexofrealms.content",
                "dev.codexofrealms.lore",
                "dev.codexofrealms.qa",
                "dev.codexofrealms.runtime"
            )
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..adapter..")
            .because("public module contracts must not expose or require adapter details")
            .check(PRODUCTION_CLASSES);
    }
}
