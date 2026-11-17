package dev.codearena;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Fails the build when a module uses another module's internals or when modules
 * depend on each other in a cycle. Also writes C4 / UML diagrams of the modules
 * to target/spring-modulith-docs.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(CodeArenaApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void writesArchitectureDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
