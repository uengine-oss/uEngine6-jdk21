package org.uengine.hwlife.esbclient.dto;

/**
 * ESB 전문에서 공통으로 쓰는 코드·포맷 상수.
 */
public final class EsbCodes {

    /** 처리결과구분코드: 정상 */
    public static final String PRCS_RSLT_SUCCESS = "0";

    /** 처리결과구분코드: 실패 (시스템) */
    public static final String PRCS_RSLT_FAILED = "1";

    /** 업무 결과코드 — 정상 ({@code payload.prcsRsltCntn} 등) */
    public static final String MSGE_CODE_SUCCESS = "LBM000000";

    /** 업무 결과코드 — 시스템 실패 (payload 상세용) */
    public static final String MSGE_CODE_SYSTEM_FAILED = "LBM999999";

    /** ESB 일시 포맷 ({@code tlgrCretDttm}, {@code rqstDttm}, {@code tlgrRspnDttm} …) */
    public static final String DTTM = "yyyyMMddHHmmssSSS";

    private EsbCodes() {
    }

    public static boolean isSuccessCode(String prcsRsltDvsnCode) {
        return PRCS_RSLT_SUCCESS.equals(prcsRsltDvsnCode);
    }
}
