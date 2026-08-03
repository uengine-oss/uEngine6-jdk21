package org.uengine.hwlife.esbclient.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ESB 응답 봉투 — {@code { "header": {...}, "payload": {...} }}.
 *
 * <p>{@code payload} 는 null 이어도 키를 유지한다({@code "payload": null}).</p>
 */
public class EsbResponse<T> {

    private EsbCommonHeader header;
    private T payload;

    public EsbResponse() {
    }

    public EsbResponse(EsbCommonHeader header, T payload) {
        this.header = header;
        this.payload = payload;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private EsbCommonHeader header;
        private T payload;

        public Builder<T> header(EsbCommonHeader header) {
            this.header = header;
            return this;
        }

        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        public EsbResponse<T> build() {
            return new EsbResponse<>(header, payload);
        }
    }

    public EsbCommonHeader getHeader() { return header; }
    public void setHeader(EsbCommonHeader header) { this.header = header; }

    /** null 이어도 {@code "payload": null} 로 직렬화한다. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public T getPayload() { return payload; }

    public void setPayload(T payload) { this.payload = payload; }
}
