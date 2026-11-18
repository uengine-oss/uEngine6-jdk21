package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.uengine.contexts.UserContext;
import org.uengine.five.entity.ProcessInstanceEntity;
import org.uengine.five.repository.ProcessInstanceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class InstanceAttributesTest {
    InstanceServiceImpl service;
    ProcessInstanceEntity entity;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setup() {
        service = new InstanceServiceImpl();
        service.processInstanceRepository = mock(ProcessInstanceRepository.class);
        entity = new ProcessInstanceEntity();
        entity.setInstId(42L);
        entity.setName("actual name");
        entity.setInfo("untouched");
        entity.setExt1("original");
        when(service.processInstanceRepository.findById(42L)).thenReturn(Optional.of(entity));
        UserContext.getThreadLocalInstance().setUserId("hong");
    }
    @AfterEach void cleanup() { UserContext.getThreadLocalInstance().setUserId(null); }

    @Test void rowsUseActualValuesWithoutRelationsOrLob() {
        var rows = service.getInstanceAttributes(42L);
        assertEquals("actual name", rows.stream().filter(r -> r.get("key").equals("name")).findFirst().orElseThrow().get("value"));
        assertTrue(rows.stream().noneMatch(r -> java.util.Set.of("instId", "variablesPath", "varLob", "roleMappings", "workLists", "dummyRoleMappings").contains(r.get("key"))));
        assertTrue(rows.stream().anyMatch(r -> r.get("key").equals("status")));
        assertTrue(rows.stream().allMatch(r -> !r.containsKey("editable")));
    }
    @Test void patchSupportsNullEmptyUnicodeAndPreservesOmittedValues() throws Exception {
        Map<String, JsonNode> patch = mapper.readValue("{\"name\":\"새 이름\",\"ext1\":null,\"ext2\":\"\"}", new TypeReference<>() {});
        service.updateInstanceAttributes(42L, patch);
        assertEquals("새 이름", entity.getName());
        assertNull(entity.getExt1());
        assertEquals("", entity.getExt2());
        assertEquals("untouched", entity.getInfo());
        verify(service.processInstanceRepository).save(entity);
    }
    @Test void invalidMixedPatchNeverChangesEntity() {
        for (String key : new String[]{"ext1", "unknown"}) {
            for (JsonNode invalid : new JsonNode[]{mapper.valueToTree(1), mapper.valueToTree(true), mapper.createObjectNode(), mapper.createArrayNode(), mapper.valueToTree("x".repeat(256))}) {
                Map<String, JsonNode> patch = new LinkedHashMap<>();
                patch.put("name", mapper.valueToTree("must not save"));
                patch.put(key, invalid);
                assertThrows(ResponseStatusException.class, () -> service.updateInstanceAttributes(42L, patch));
                assertEquals("actual name", entity.getName());
            }
        }
        verify(service.processInstanceRepository, never()).save(any());
    }
    @Test void actualScalarTypesAndExactLongRoundTrip() throws Exception {
        for (String number : new String[]{"-9223372036854775808", "9223372036854775807", "9007199254740993"}) {
            var rows = service.updateInstanceAttributes(42L, Map.of("mainInstId", mapper.valueToTree(number)));
            assertEquals(number, rows.stream().filter(r -> r.get("key").equals("mainInstId")).findFirst().orElseThrow().get("value"));
            assertEquals(Long.parseLong(number), entity.getMainInstId());
        }
        service.updateInstanceAttributes(42L, mapper.readValue("{\"status\":\"Running\",\"archive\":true,\"dueDate\":\"2026-11-18\"}", new TypeReference<Map<String, JsonNode>>() {}));
        assertEquals("Running", entity.getStatus()); assertTrue(entity.isArchive()); assertNotNull(entity.getDueDate());
    }
    @Test void identityPathsNullAndConversionFailuresNeverPartiallyWrite() {
        Map<String, JsonNode> invalids = Map.ofEntries(
            Map.entry("instId", mapper.valueToTree(99)), Map.entry("class.name", mapper.valueToTree("x")),
            Map.entry("variablesPath", mapper.valueToTree("must not write")),
            Map.entry("mainInstId", mapper.valueToTree("9223372036854775808")),
            Map.entry("dueDate", mapper.valueToTree("2026-02-30")), Map.entry("archive", mapper.nullNode()));
        for (var bad : invalids.entrySet()) {
            Map<String, JsonNode> patch = new LinkedHashMap<>(); patch.put("name", mapper.valueToTree("must not save")); patch.put(bad.getKey(), bad.getValue());
            assertThrows(ResponseStatusException.class, () -> service.updateInstanceAttributes(42L, patch));
            assertEquals("actual name", entity.getName());
        }
        assertThrows(ResponseStatusException.class, () -> service.updateInstanceAttributes(42L, Map.of("dueDate", mapper.valueToTree(1.5))));
        assertThrows(ResponseStatusException.class, () -> service.updateInstanceAttributes(42L, Map.of("dueDate", mapper.valueToTree(Long.MAX_VALUE))));
        assertThrows(ResponseStatusException.class, () -> service.updateInstanceAttributes(42L, Map.of("mainInstId", mapper.valueToTree(1.5))));
        verify(service.processInstanceRepository, never()).save(any());
    }
}
