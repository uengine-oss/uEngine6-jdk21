package org.uengine.five.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 IAM provider의 프로세스 설계용 기관·권한 후보를 공통 형식으로 제공합니다.
 * 외부 IAM REST 계약과 Keycloak 구현을 서로 참조하지 않도록 별도 경계로 둡니다.
 */
@RestController
@RequestMapping("/iam/candidates")
public class IAMCandidateController {

    @PostMapping("/orgs")
    public List<Map<String, String>> getOrganizations() throws Exception {
        List<Map<String, String>> result = new ArrayList<>();
        addOrganizations(getIamService().getGroupCandidates(), result);
        return result;
    }

    @PostMapping("/roles")
    public List<Map<String, String>> getRoles() throws Exception {
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> candidate : getIamService().getRoleCandidates()) {
            Map<String, String> normalized = normalize(candidate);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void addOrganizations(
            List<Map<String, Object>> candidates, List<Map<String, String>> result) {
        for (Map<String, Object> candidate : candidates) {
            Map<String, String> normalized = normalize(candidate);
            if (normalized != null) {
                result.add(normalized);
            }
            if (candidate.get("subGroups") instanceof List<?> children) {
                addOrganizations((List<Map<String, Object>>) (List<?>) children, result);
            }
        }
    }

    private static Map<String, String> normalize(Map<String, Object> candidate) {
        if (candidate == null) {
            return null;
        }
        String id = text(candidate.get("id"));
        if (id == null) {
            id = text(candidate.get("name"));
        }
        if (id == null) {
            return null;
        }
        String name = text(candidate.get("displayName"));
        if (name == null) {
            name = text(candidate.get("name"));
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        normalized.put("id", id);
        normalized.put("name", name != null ? name : id);
        return normalized;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    protected IAMService getIamService() {
        return IAMServiceFactory.getDefault();
    }
}
