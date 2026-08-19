package org.uengine.five.rpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RpaValidationServiceTest {

    private final RpaValidationService service = new RpaValidationService();

    @Test
    void acceptsGeneratedScriptPolicy() {
        String script = """
                *** Settings ***
                Library    UEngineLibrary

                *** Tasks ***
                RPA 작업
                    Log To Console    시작
                """;

        assertTrue(service.validatePolicy(script).isEmpty());
    }

    @Test
    void rejectsArbitraryLibraryAndResourceImports() {
        String script = """
                *** Settings ***
                Library    Process
                Resource   secrets.robot
                """;

        var errors = service.validatePolicy(script);
        assertEquals(2, errors.size());
        assertTrue(errors.get(0).message().contains("허용되지 않은"));
        assertTrue(errors.get(1).message().contains("Resource"));
    }

    @Test
    void rejectsEmptyScript() {
        assertEquals(1, service.validatePolicy(" ").size());
    }
}
