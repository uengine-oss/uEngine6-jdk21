package org.uengine.hwlife.esbclient.client;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.uengine.hwlife.esbclient.dto.EsbCodes;
import org.uengine.hwlife.esbclient.dto.EsbCommonHeader;
import org.uengine.hwlife.esbclient.dto.EsbMessage;
import org.uengine.hwlife.esbclient.dto.EsbRequest;
import org.uengine.hwlife.esbclient.dto.EsbResponse;
import org.uengine.hwlife.esbclient.exception.EsbException;
import org.uengine.hwlife.esbclient.factory.EsbHeaderFactory;
import org.uengine.hwlife.esbclient.support.EsbEnvelope;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EsbClientImpl implements EsbClient {

    private static final Logger log = LoggerFactory.getLogger(EsbClientImpl.class);

    private final RestTemplate restTemplate;
    private final EsbHeaderFactory headerFactory;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public EsbClientImpl(
            @Qualifier("esbRestTemplate") RestTemplate restTemplate,
            EsbHeaderFactory headerFactory,
            @Value("${esb.base-url:}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.headerFactory = headerFactory;
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public <T, R> R send(String itfcId, String rcveSrvcId, T payload, Class<R> responseType) {
        EsbCommonHeader header = headerFactory.create(itfcId, rcveSrvcId);
        EsbRequest<T> request = EsbRequest.<T>builder()
                .header(header)
                .payload(payload)
                .build();

        EsbResponse<R> response = exchange(requireBaseUrl(), request, responseType);
        return response != null ? response.getPayload() : null;
    }

    private String requireBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new EsbException("esb.base-url is not configured (check spring.profiles.active)");
        }
        return baseUrl;
    }

    private <T, R> EsbResponse<R> exchange(String url, EsbRequest<T> request, Class<R> responseType) {
        if (url == null || url.isBlank()) {
            throw new EsbException("ESB url is empty");
        }

        String itfcId = request != null && request.getHeader() != null
                ? request.getHeader().getItfcId() : null;
        log.debug("[EsbClient] POST {} | itfcId={}", url, itfcId);

        try {
            HttpEntity<EsbRequest<T>> entity = new HttpEntity<>(request);
            ResponseEntity<String> http = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<String>() {});

            String body = http.getBody();
            if (body == null || body.isBlank()) {
                throw new EsbException("ESB Response is null", http.getStatusCode().value(), body);
            }

            EsbResponse<R> result = EsbEnvelope.parseResponse(objectMapper, body, responseType);
            assertBusinessSuccess(result.getHeader(), http.getStatusCode().value(), body);
            return result;
        } catch (EsbException e) {
            throw e;
        } catch (RestClientResponseException e) {
            // HTTP 에러 — ESB 응답 본문을 그대로 전달
            String body = e.getResponseBodyAsString();
            String message = (body != null && !body.isBlank()) ? body : e.getMessage();
            throw new EsbException(message, e.getStatusCode().value(), body, e);
        } catch (Exception e) {
            throw new EsbException(e.getMessage() != null ? e.getMessage() : e.toString(), e);
        }
    }

    /**
     * 전문 헤더 처리결과가 실패이면 ESB 메시지 내용을 그대로 예외로 던진다.
     */
    private void assertBusinessSuccess(EsbCommonHeader header, int httpStatus, String rawBody) {
        if (header == null) {
            return;
        }
        String resultCode = header.getPrcsRsltDvsnCode();
        if (resultCode == null || resultCode.isBlank() || EsbCodes.isSuccessCode(resultCode)) {
            return;
        }
        throw new EsbException(extractEsbErrorMessage(header, rawBody), httpStatus, rawBody);
    }

    private static String extractEsbErrorMessage(EsbCommonHeader header, String rawBody) {
        List<EsbMessage> list = header.getMsgeList();
        if (list != null && !list.isEmpty()) {
            String joined = list.stream()
                    .filter(m -> m != null)
                    .map(m -> {
                        if (m.getMsgeCntn() != null && !m.getMsgeCntn().isBlank()) {
                            return m.getMsgeCode() != null
                                    ? m.getMsgeCode() + ": " + m.getMsgeCntn()
                                    : m.getMsgeCntn();
                        }
                        return m.getMsgeCode();
                    })
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" | "));
            if (!joined.isBlank()) {
                return joined;
            }
        }
        if (header.getMsgeStackTrace() != null && !header.getMsgeStackTrace().isBlank()) {
            return header.getMsgeStackTrace();
        }
        if (rawBody != null && !rawBody.isBlank()) {
            return rawBody;
        }
        return "prcsRsltDvsnCode=" + header.getPrcsRsltDvsnCode();
    }
}
