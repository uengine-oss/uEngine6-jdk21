package org.uengine.hwlife.esbclient.client;

import org.uengine.hwlife.esbclient.dto.EsbRequest;
import org.uengine.hwlife.esbclient.dto.EsbResponse;

public interface EsbClient {

    /**
     * itfcId + payload 로 ESB 호출. 응답 payload 만 반환.
     */
    <T, R> R send(String itfcId, T payload, Class<R> responseType);

    /**
     * uri 지정 호출. 응답 전체({@link EsbResponse}) 반환.
     */
    <T, R> EsbResponse<R> send(String uri, EsbRequest<T> request, Class<R> responseType);
}
