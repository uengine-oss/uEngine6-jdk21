package org.uengine.kernel;

import org.junit.Test;
import org.uengine.contexts.EventSynchronization;
import org.uengine.contexts.MappingContext;
import org.uengine.contexts.TextContext;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class ReceiveActivityMappingInTest {
    @Test
    public void configuredEventStillMapsProcessVariable() throws Exception {
        ReceiveActivity activity = new ReceiveActivity();
        EventSynchronization event = new EventSynchronization();
        FieldDescriptor field = new FieldDescriptor();
        field.setName("result");
        field.setClassName("String");
        event.setAttributes(new FieldDescriptor[] { field });
        MappingElement element = new MappingElement();
        TextContext argument = TextContext.createInstance();
        argument.setText("[Arguments].result");
        element.setArgument(argument);
        ProcessVariable variable = new ProcessVariable();
        variable.setName("input");
        element.setVariable(variable);
        MappingContext mapping = new MappingContext();
        mapping.setMappingElements(new MappingElement[] { element });
        event.setMappingContext(mapping);
        activity.setEventSynchronization(event);
        DefaultProcessInstance instance = new DefaultProcessInstance() {
            @Override
            public Object getBeanProperty(String key) {
                assertEquals("input", key);
                return "preserved";
            }
        };
        assertEquals("preserved", activity.getMappingInValues(instance).get("result"));
    }

    @Test
    public void humanTaskWithoutEventSynchronizationHasNoEventInputs() throws Exception {
        assertTrue(new HumanActivity().getMappingInValues(null).isEmpty());
    }

    @Test
    public void eventWithoutMappingContextHasNoEventInputs() throws Exception {
        ReceiveActivity activity = new ReceiveActivity();
        activity.setEventSynchronization(new EventSynchronization());
        assertTrue(activity.getMappingInValues(null).isEmpty());
    }

    @Test
    public void eventWithoutMappingElementsHasNoEventInputs() throws Exception {
        ReceiveActivity activity = new ReceiveActivity();
        EventSynchronization event = new EventSynchronization();
        event.setMappingContext(new MappingContext());
        activity.setEventSynchronization(event);
        assertTrue(activity.getMappingInValues(null).isEmpty());
    }

    @Test
    public void declaredInputsRemainAvailableWithoutAMapping() throws Exception {
        ReceiveActivity activity = new ReceiveActivity();
        EventSynchronization event = new EventSynchronization();
        FieldDescriptor field = new FieldDescriptor();
        field.setName("amount");
        field.setClassName("Number");
        event.setAttributes(new FieldDescriptor[] { field });
        activity.setEventSynchronization(event);
        assertTrue(activity.getMappingInValues(null).containsKey("amount"));
    }

    @Test
    public void mappingWithoutAttributesDoesNotDereferenceMissingSchema() throws Exception {
        ReceiveActivity activity = new ReceiveActivity();
        EventSynchronization event = new EventSynchronization();
        event.setMappingContext(new MappingContext());
        event.getMappingContext().setMappingElements(new MappingElement[0]);
        activity.setEventSynchronization(event);
        assertTrue(activity.getMappingInValues(null).isEmpty());
    }
}
