package com.orthiva.core;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architecture guardrails. Fails the build when a module reaches into another module's
 * internals (anything outside its root package) or when modules form a cycle. Also
 * regenerates the module diagrams under target/spring-modulith-docs (copied to docs/).
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(OrthivaCoreApplication.class);

    @Test
    void modulesRespectBoundaries() {
        modules.verify();
    }

    @Test
    void writeDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
