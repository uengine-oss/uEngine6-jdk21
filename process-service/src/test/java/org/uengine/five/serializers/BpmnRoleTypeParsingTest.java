package org.uengine.five.serializers;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.uengine.hwlife.overriding.BoundRoleResolutionContext;

class BpmnRoleTypeParsingTest {
    private String xml(String context) {
        return """
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:uengine="http://uengine.org">
              <bpmn:process id="process" isExecutable="true">
                <bpmn:laneSet id="lanes"><bpmn:lane id="lane" name="handler">
                  <bpmn:extensionElements><uengine:properties><uengine:json><![CDATA[
                  {"roleResolutionContext": %s}
                  ]]></uengine:json></uengine:properties></bpmn:extensionElements>
                </bpmn:lane></bpmn:laneSet>
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(context);
    }

    @Test
    void validBoundBasesPreserveTypeAndBindings() throws Exception {
        for (String type : new String[] { "org.uengine.kernel.DirectRoleResolutionContext",
                "org.uengine.five.overriding.IAMRoleResolutionContext",
                "org.uengine.hwlife.overriding.RuleBasedRoleResolutionContext" }) {
            String context = """
                {"_type":"org.uengine.hwlife.overriding.BoundRoleResolutionContext",
                 "base":{"_type":"%s"}, "bindings":{"endpoint":"handler"}}
                """.formatted(type);
            var role = new BpmnXMLParser().parse(xml(context)).getRoles()[0];
            var bound = assertInstanceOf(BoundRoleResolutionContext.class, role.getRoleResolutionContext());
            assertEquals(type, bound.getBase().getClass().getName());
            assertEquals("handler", bound.getBindings().get("endpoint"));
        }
    }

    @Test
    void missingNestedTypeReportsActualCause() {
        var error = assertThrows(RuntimeException.class, () -> new BpmnXMLParser().parse(xml("""
            {"_type":"org.uengine.hwlife.overriding.BoundRoleResolutionContext","base":{},"bindings":{}}
            """)));
        assertTrue(error.getMessage().contains("missing type id property '_type'"));
        assertFalse(error.getMessage().contains("classpath: null"));
        assertNotNull(error.getCause());
    }

    @Test
    void unknownClassRemainsAnErrorWithItsName() {
        var error = assertThrows(RuntimeException.class,
                () -> new BpmnXMLParser().parse(xml("{\"_type\":\"example.MissingRoleContext\"}")));
        assertTrue(error.getMessage().contains("example.MissingRoleContext"));
        assertNotNull(error.getCause());
    }
}
