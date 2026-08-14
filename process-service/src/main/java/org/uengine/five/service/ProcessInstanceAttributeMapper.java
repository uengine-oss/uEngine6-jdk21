package org.uengine.five.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.uengine.five.entity.ProcessInstanceEntity;

@Component
public class ProcessInstanceAttributeMapper {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

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

        applyEsbHeaderInitiator(instance, payload);
    }

    /**
     * 이벤트 inbox payload 의 {@code esbHeader} 로 최초 담당자(init) 필드를 설정한다.
     * 인스턴스 생성 시점({@code InstanceServiceImpl#start}) 에만 적용되며,
     * 워크리스트/RoleMapping 경로에서는 init_ep·init_group_cd 를 채우지 않는다.
     */
    @SuppressWarnings("unchecked")
    private void applyEsbHeaderInitiator(ProcessInstanceEntity instance, Map<String, Object> payload) {
        Object esbHeaderObj = payload.get("esbHeader");
        if (!(esbHeaderObj instanceof Map<?, ?> esbHeader)) {
            return;
        }
        String emnb = text(esbHeader.get("emnb"));
        String belnOrgnCode = text(esbHeader.get("belnOrgnCode"));
        if (emnb != null) {
            instance.setInitEp(emnb);
        }
        if (belnOrgnCode != null) {
            instance.setInitGroupCd(belnOrgnCode);
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /** yyyyMMdd (예: 20260807) */
    private static Date date(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            LocalDate localDate = LocalDate.parse(text, YMD);
            return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            return null;
        }
    }
}
