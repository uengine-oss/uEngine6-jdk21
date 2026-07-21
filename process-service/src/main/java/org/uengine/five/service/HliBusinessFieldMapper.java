package org.uengine.five.service;

import java.util.Date;
import java.util.Map;

import org.uengine.five.dto.ProcessExecutionCommand;
import org.uengine.five.entity.ProcessInstanceEntity;

public final class HliBusinessFieldMapper {

    private HliBusinessFieldMapper() {
    }

    public static void copy(ProcessExecutionCommand command, ProcessInstanceEntity entity) {
        if (command == null || entity == null) {
            return;
        }
        entity.setCustNo(asString(hliValue(command, "cusNo", "custNo")));
        entity.setFncgBswrDvsnCode(asString(hliValue(command, "fncgBswrDvsnCode")));
        entity.setLoanCntcNo(asString(hliValue(command, "loanCntcNo")));
        entity.setFncgSuptTrgtDvsnCode(asString(hliValue(command, "fncgSuptTrgtDvsnCode")));
        entity.setLoanSubjDvsnCode(asString(hliValue(command, "loanSubjDvsnCode")));
        entity.setLaonHopeDate(asDate(hliValue(command, "loanHopeDate", "laonHopeDate")));
        entity.setFncgMneyUsagClsfCode(asString(hliValue(command, "fncgMneyUsagClsfCode")));
        entity.setBsnsClsf(asString(hliValue(command, "bswrClsfCode", "bsnsClsf")));
    }

    private static Object hliValue(ProcessExecutionCommand command, String... names) {
        for (String name : names) {
            Object payloadValue = valueFromPayload(command.getStartEventPayload(), name);
            if (payloadValue != null) {
                return payloadValue;
            }
        }

        org.uengine.five.dto.ProcessVariableValue[] processVariableValues = command.getProcessVariableValues();
        if (processVariableValues == null) {
            return null;
        }
        for (String name : names) {
            for (org.uengine.five.dto.ProcessVariableValue pv : processVariableValues) {
                if (pv == null || !name.equals(pv.getName())) {
                    continue;
                }
                return pv.getValues() != null && pv.getValues().length > 0 ? pv.getValues()[0] : null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object valueFromPayload(Map<String, Object> payload, String name) {
        if (payload == null) {
            return null;
        }
        if (payload.containsKey(name)) {
            return payload.get(name);
        }
        Object nestedPayload = payload.get("payload");
        if (nestedPayload instanceof Map) {
            return valueFromPayload((Map<String, Object>) nestedPayload, name);
        }
        return null;
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Date asDate(Object value) {
        if (value instanceof Date) {
            return (Date) value;
        }
        String text = asString(value);
        if (text == null) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(text);
        } catch (Exception ignore) {
            return null;
        }
    }
}
