package org.uengine.hwlife.esbclient.exception;

/**
 * ESB 호출 실패. 메시지는 ESB가 내려준 에러 내용 그대로다.
 */
public class EsbException extends RuntimeException {

    private final Integer httpStatus;
    private final String rawBody;

    public EsbException(String message) {
        this(message, null, null, null);
    }

    public EsbException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public EsbException(String message, Integer httpStatus, String rawBody) {
        this(message, httpStatus, rawBody, null);
    }

    public EsbException(String message, Integer httpStatus, String rawBody, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.rawBody = rawBody;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    /** ESB 응답 원문 (있으면). */
    public String getRawBody() {
        return rawBody;
    }
}
