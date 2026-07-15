package org.uengine.hwlife.esbclient.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ESB 응답 봉투 — {@code { "header": {...}, "payload": {...} }}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EsbResponse<T> {

    private EsbCommonHeader header;
    private T payload;

    public EsbResponse() {
    }

    public EsbResponse(EsbCommonHeader header, T payload) {
        this.header = header;
        this.payload = payload;
    }

    public EsbCommonHeader getHeader() { return header; }
    public void setHeader(EsbCommonHeader header) { this.header = header; }
    public T getPayload() { return payload; }
    public void setPayload(T payload) { this.payload = payload; }
}
