package org.uengine.five.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.uengine.five.entity.ProcessInstanceEntity;

@Component
public class ProcessInstanceAttributeMapper {

    public void apply(ProcessInstanceEntity instance, Map<String, Object> payload) {
        if (instance == null || payload == null || payload.isEmpty()) {
            return;
        }

        instance.setCustId(text(payload.get("custId")));
        instance.setLoanCntcNo(text(payload.get("loanCntcNo")));
        instance.setFncgBswrDvsnCode(text(payload.get("fncgBswrDvsnCode")));
        instance.setFncgSuptTrgtDvsnCode(text(payload.get("fncgSuptTrgtDvsnCode")));
        instance.setLoanSubjDvsnCode(text(payload.get("loanSubjDvsnCode")));
        instance.setFncgMneyUsagClsfCode(text(payload.get("fncgMneyUsagClsfCode")));
        instance.setBswrClsfCode(text(payload.get("bpmBswrClsfCode")));

        Date loanHopeDate = date(payload.get("loanHopeDate"));
        if (loanHopeDate != null) {
            instance.setLoanHopeDate(loanHopeDate);
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Date date(Object value) {
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        }

        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return Date.from(Instant.parse(text));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Date.from(OffsetDateTime.parse(text).toInstant());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Date.from(LocalDate.parse(text)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
