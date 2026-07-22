package org.uengine.hwlife.esbclient.dto;

import java.time.format.DateTimeFormatter;

/**
 * ESB 전문에서 공통으로 쓰는 코드·포맷 상수.
 */
public final class EsbCodes {

    /** 처리결과구분코드: 정상 */
    public static final String PRCS_RSLT_SUCCESS = "0";

    /** 처리결과구분코드: 실패 */
    public static final String PRCS_RSLT_FAILED = "1";

    /** ESB 일시 포맷 ({@code tlgrCretDttm}, {@code rqstDttm}, {@code tlgrRspnDttm} …) */
    public static final DateTimeFormatter DTTM =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private EsbCodes() {
    }

    public static boolean isSuccessCode(String prcsRsltDvsnCode) {
        return PRCS_RSLT_SUCCESS.equals(prcsRsltDvsnCode);
    }
}
