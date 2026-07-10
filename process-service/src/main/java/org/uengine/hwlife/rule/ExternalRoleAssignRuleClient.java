package org.uengine.hwlife.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 외부 기준정보 API client.
 *
 * <p>uengine.role-assign-rule.external-url 이 비어 있으면 외부 호출 없이 빈 결과를 반환한다.</p>
 */
@Component
public class ExternalRoleAssignRuleClient {

    private final RestTemplate restTemplate;
    private final String externalUrl;

    public ExternalRoleAssignRuleClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${uengine.role-assign-rule.external-url:}") String externalUrl) {
        this.restTemplate = restTemplateBuilder.build();
        this.externalUrl = externalUrl;
    }

    public List<ExternalRoleAssignRule> fetchRules(String policyId, String difficulty) {
        if (!hasText(externalUrl)) {
            return Collections.emptyList();
        }

        String url = UriComponentsBuilder.fromHttpUrl(externalUrl)
                .queryParam("policyId", policyId)
                .queryParamIfPresent("difficulty", hasText(difficulty) ? java.util.Optional.of(difficulty) : java.util.Optional.empty())
                .toUriString();

        ResponseEntity<List<ExternalRoleAssignRule>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ExternalRoleAssignRule>>() {
                });

        List<ExternalRoleAssignRule> body = response.getBody();
        return body != null ? body : new ArrayList<>();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
