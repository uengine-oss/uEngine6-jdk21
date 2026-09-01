package org.uengine.hwlife.esbclient.client;

public interface EsbClient {

    /**
     * itfcId + rcveSrvcId + payload 로 ESB 호출. 응답 payload 만 반환.
     *
     * @param itfcId       인터페이스 아이디
     * @param rcveSrvcId   수신 서비스 아이디 (호출 측에서 전달)
     * @param payload      업무 페이로드
     * @param responseType 응답 payload 타입
     */
    <T, R> R send(String itfcId, String rcveSrvcId, T payload, Class<R> responseType);

    /**
     * itfcId + rcveSrvcId + rcveSysCode + payload 로 ESB 호출. 응답 payload 만 반환.
     *
     * @param itfcId       인터페이스 아이디
     * @param rcveSrvcId   수신 서비스 아이디 (호출 측에서 전달)
     * @param rcveSysCode  수신 시스템 코드 (null/blank 이면 LCS)
     * @param payload      업무 페이로드
     * @param responseType 응답 payload 타입
     */
    <T, R> R send(String itfcId, String rcveSrvcId, String rcveSysCode, T payload, Class<R> responseType);
}
