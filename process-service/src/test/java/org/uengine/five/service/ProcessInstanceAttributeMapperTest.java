package org.uengine.five.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.uengine.five.entity.ProcessInstanceEntity;

class ProcessInstanceAttributeMapperTest {

    private final ProcessInstanceAttributeMapper mapper = new ProcessInstanceAttributeMapper();

    @Test
    void mapsAllSupportedBusinessAttributes() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        Map<String, Object> payload = new HashMap<>();
        payload.put("custId", " C-100 ");
        payload.put("loanCntcNo", "LC-200");
        payload.put("fncgBswrDvsnCode", "HOME");
        payload.put("fncgSuptTrgtDvsnCode", "TARGET");
        payload.put("loanSubjDvsnCode", "SUBJECT");
        payload.put("loanHopeDate", "2026-08-03");
        payload.put("fncgMneyUsagClsfCode", "PURCHASE");
        payload.put("bswrClsfCode", "LOAN");
        payload.put("status", "Completed");

        mapper.apply(instance, payload);

        assertEquals("C-100", instance.getCustId());
        assertEquals("LC-200", instance.getLoanCntcNo());
        assertEquals("HOME", instance.getFncgBswrDvsnCode());
        assertEquals("TARGET", instance.getFncgSuptTrgtDvsnCode());
        assertEquals("SUBJECT", instance.getLoanSubjDvsnCode());
        assertEquals("PURCHASE", instance.getFncgMneyUsagClsfCode());
        assertEquals("LOAN", instance.getBswrClsfCode());
        assertEquals(LocalDate.of(2026, 8, 3), localDate(instance.getLoanHopeDate()));
        assertNull(instance.getStatus());
    }

    @Test
    void ignoresBlankUnknownAndInvalidDateValues() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();

        mapper.apply(instance, Map.of(
                "custId", "  ",
                "loanHopeDate", "not-a-date",
                "unknown", "value"));

        assertNull(instance.getCustId());
        assertNull(instance.getLoanHopeDate());
    }

    @Test
    void acceptsTimestampDateValue() {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        Date expected = new Date(1_785_724_800_000L);

        mapper.apply(instance, Map.of("loanHopeDate", expected));

        assertEquals(expected, instance.getLoanHopeDate());
    }

    private static LocalDate localDate(Date value) {
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
