package org.uengine.five.entity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

class EventMappingEntitySchemaTest {

    @Test
    void usesCompositeUniqueConstraintForMappingTarget() {
        Table table = EventMappingEntity.class.getAnnotation(Table.class);
        UniqueConstraint[] constraints = table.uniqueConstraints();

        assertEquals(1, constraints.length);
        assertEquals("uk_event_mapping_target", constraints[0].name());
        assertArrayEquals(
                new String[] {"event_name", "definition_id", "tracing_tag", "is_start_event"},
                constraints[0].columnNames());
    }
}
