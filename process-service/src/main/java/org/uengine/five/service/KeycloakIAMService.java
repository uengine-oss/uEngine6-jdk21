package org.uengine.five.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.uengine.kernel.GlobalContext;
import org.uengine.util.UEngineUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Keycloak Admin API를 통한 사용자·그룹·역할 정보 조회 IAMService 구현체.
 *
 * <p>Spring Bean이 아닌 싱글톤 팩토리 메서드({@link #getDefault()})로 인스턴스를 관리합니다.
 * 애플리케이션 기동 시 {@code IAMServiceFactory.register("keycloak", KeycloakIAMService.getDefault())}
 * 로 등록해야 합니다.</p>
 */
public class KeycloakIAMService implements IAMService {

    private static KeycloakIAMService defaultService;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String JSON_CONTENT_TYPE = "application/json";

    private String keycloakUrl;
    private String realm;
    private String adminClientId;
    private String adminClientSecret;
    private String adminUsername;
    private String adminPassword;
    private String adminRealm;

    private KeycloakIAMService() {
        this.keycloakUrl = System.getenv("KEYCLOAK_URI");
        if (!UEngineUtil.isNotEmpty(this.keycloakUrl)) {
            this.keycloakUrl = GlobalContext.getPropertyString("keycloak.uri", "http://localhost:8080");
        }

        this.realm = System.getenv("KEYCLOAK_REALM");
        if (!UEngineUtil.isNotEmpty(this.realm)) {
            this.realm = GlobalContext.getPropertyString("keycloak.realm", "uengine");
        }

        // Admin token realm (보통 master realm에서 admin 토큰을 발급받아 Admin API 호출)
        this.adminRealm = System.getenv("KEYCLOAK_ADMIN_REALM");
        if (!UEngineUtil.isNotEmpty(this.adminRealm)) {
            this.adminRealm = GlobalContext.getPropertyString("keycloak.admin.realm", "uengine");
        }

        this.adminClientId = System.getenv("KEYCLOAK_ADMIN_CLIENT_ID");
        if (!UEngineUtil.isNotEmpty(this.adminClientId)) {
            this.adminClientId = GlobalContext.getPropertyString("keycloak.admin.client.id", "admin-cli");
        }

        this.adminClientSecret = System.getenv("KEYCLOAK_ADMIN_CLIENT_SECRET");
        if (!UEngineUtil.isNotEmpty(this.adminClientSecret)) {
            this.adminClientSecret = GlobalContext.getPropertyString("keycloak.admin.client.secret", null);
        }

        this.adminUsername = System.getenv("KEYCLOAK_ADMIN_USERNAME");
        if (!UEngineUtil.isNotEmpty(this.adminUsername)) {
            this.adminUsername = System.getenv("KEYCLOAK_ADMIN");
        }
        if (!UEngineUtil.isNotEmpty(this.adminUsername)) {
            this.adminUsername = GlobalContext.getPropertyString("keycloak.admin.username", "admin");
        }

        this.adminPassword = System.getenv("KEYCLOAK_ADMIN_PASSWORD");
        if (!UEngineUtil.isNotEmpty(this.adminPassword)) {
            this.adminPassword = GlobalContext.getPropertyString("keycloak.admin.password", "admin");
        }
    }

    public static synchronized KeycloakIAMService getDefault() {
        if (defaultService == null) {
            defaultService = new KeycloakIAMService();
        }
        return defaultService;
    }

    @Override
    public String getProviderId() {
        return "keycloak";
    }

    private String getAdminAccessToken() throws IOException, InterruptedException, URISyntaxException {
        String tokenUrl = keycloakUrl + "/realms/" + adminRealm + "/protocol/openid-connect/token";

        Map<String, String> form = new LinkedHashMap<>();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(new URI(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded");

        // 1) client_credentials (client secret이 있을 때)
        if (UEngineUtil.isNotEmpty(adminClientSecret)) {
            form.put("grant_type", "client_credentials");
            form.put("client_id", adminClientId);
            form.put("client_secret", adminClientSecret);
        } else if (UEngineUtil.isNotEmpty(adminUsername) && UEngineUtil.isNotEmpty(adminPassword)) {
            // 2) password grant (admin user)
            form.put("grant_type", "password");
            form.put("client_id", adminClientId);
            form.put("username", adminUsername);
            form.put("password", adminPassword);
        } else {
            throw new RuntimeException(
                    "Keycloak admin token configuration is missing. " +
                    "Set either (KEYCLOAK_ADMIN_CLIENT_ID + KEYCLOAK_ADMIN_CLIENT_SECRET) for client_credentials, " +
                    "or (KEYCLOAK_ADMIN_USERNAME/KEYCLOAK_ADMIN + KEYCLOAK_ADMIN_PASSWORD) for password grant.");
        }

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> tokenResponse = objectMapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});
            return (String) tokenResponse.get("access_token");
        } else {
            throw new RuntimeException("Failed to get admin access token: " + response.statusCode() + " - " + response.body());
        }
    }

    private static String formEncode(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(e.getValue() != null ? e.getValue() : "", StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }

    /**
     * 사용자 검색: username exact → email exact → search(부분) 순으로 시도
     */
    private List<Map<String, Object>> searchUsers(String userIdOrEmail, String accessToken) throws Exception {
        String q = urlEncode(userIdOrEmail);

        List<String> queries = new ArrayList<>();
        queries.add("username=" + q + "&exact=true");
        if (userIdOrEmail != null && userIdOrEmail.contains("@")) {
            queries.add("email=" + q + "&exact=true");
        }
        queries.add("search=" + q);

        for (String query : queries) {
            String url = keycloakUrl + "/admin/realms/" + realm + "/users?" + query;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", JSON_CONTENT_TYPE)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<Map<String, Object>> users = objectMapper.readValue(response.body(),
                        new TypeReference<List<Map<String, Object>>>() {});
                if (users != null && !users.isEmpty()) {
                    return users;
                }
            } else {
                throw new RuntimeException("Failed to search users: " + response.statusCode() + " - " + response.body());
            }
        }

        return new ArrayList<>();
    }

    private static String normalizeGroupName(String groupName) {
        if (groupName == null) return null;
        // Keycloak group path는 "/DevTeam" 형태, name은 "DevTeam" 형태
        if (groupName.startsWith("/")) return groupName.substring(1);
        return groupName;
    }

    @Override
    public Map<String, Object> getUserById(String userId) throws Exception {
        String accessToken = getAdminAccessToken();
        List<Map<String, Object>> users = searchUsers(userId, accessToken);
        if (users != null && !users.isEmpty()) {
            return users.get(0);
        }
        return null;
    }

    @Override
    public List<String> getUsersByGroup(String groupName) throws Exception {
        String accessToken = getAdminAccessToken();

        String groupSearchUrl = keycloakUrl + "/admin/realms/" + realm + "/groups?search=" + groupName;
        HttpRequest groupRequest = HttpRequest.newBuilder()
                .uri(new URI(groupSearchUrl))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse<String> groupResponse = httpClient.send(groupRequest, HttpResponse.BodyHandlers.ofString());

        if (groupResponse.statusCode() != 200) {
            throw new RuntimeException("Failed to find group: " + groupResponse.statusCode());
        }

        List<Map<String, Object>> groups = objectMapper.readValue(groupResponse.body(),
                new TypeReference<List<Map<String, Object>>>() {});

        if (groups == null || groups.isEmpty()) {
            return new ArrayList<>();
        }

        String groupId = (String) groups.get(0).get("id");

        String membersUrl = keycloakUrl + "/admin/realms/" + realm + "/groups/" + groupId + "/members";
        HttpRequest membersRequest = HttpRequest.newBuilder()
                .uri(new URI(membersUrl))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse<String> membersResponse = httpClient.send(membersRequest, HttpResponse.BodyHandlers.ofString());

        if (membersResponse.statusCode() == 200) {
            List<Map<String, Object>> users = objectMapper.readValue(membersResponse.body(),
                    new TypeReference<List<Map<String, Object>>>() {});

            List<String> userIds = new ArrayList<>();
            for (Map<String, Object> user : users) {
                String username = (String) user.get("username");
                if (username != null) userIds.add(username);
            }
            return userIds;
        }

        return new ArrayList<>();
    }

    @Override
    public List<String> getUsersByRole(String roleName) throws Exception {
        String accessToken = getAdminAccessToken();
        String url = keycloakUrl + "/admin/realms/" + realm + "/roles/" + roleName + "/users";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            List<Map<String, Object>> users = objectMapper.readValue(response.body(),
                    new TypeReference<List<Map<String, Object>>>() {});

            List<String> userIds = new ArrayList<>();
            for (Map<String, Object> user : users) {
                String username = (String) user.get("username");
                if (username != null) userIds.add(username);
            }
            return userIds;
        }

        return new ArrayList<>();
    }

    @Override
    public List<String> getUserScopes(String userId) throws Exception {
        String accessToken = getAdminAccessToken();

        List<Map<String, Object>> users = searchUsers(userId, accessToken);
        if (users == null || users.isEmpty()) {
            return new ArrayList<>();
        }

        String userKeycloakId = (String) users.get(0).get("id");

        String rolesUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + userKeycloakId + "/role-mappings/realm";
        HttpRequest rolesRequest = HttpRequest.newBuilder()
                .uri(new URI(rolesUrl))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse<String> rolesResponse = httpClient.send(rolesRequest, HttpResponse.BodyHandlers.ofString());

        if (rolesResponse.statusCode() == 200) {
            List<Map<String, Object>> roles = objectMapper.readValue(rolesResponse.body(),
                    new TypeReference<List<Map<String, Object>>>() {});

            List<String> roleNames = new ArrayList<>();
            for (Map<String, Object> role : roles) {
                String roleName = (String) role.get("name");
                if (roleName != null) roleNames.add(roleName);
            }
            return roleNames;
        }

        return new ArrayList<>();
    }

    @Override
    public List<String> getUserGroups(String userId) throws Exception {
        String accessToken = getAdminAccessToken();

        List<Map<String, Object>> users = searchUsers(userId, accessToken);
        if (users == null || users.isEmpty()) {
            return new ArrayList<>();
        }

        String userKeycloakId = (String) users.get(0).get("id");

        String groupsUrl = keycloakUrl + "/admin/realms/" + realm + "/users/" + userKeycloakId + "/groups";
        HttpRequest groupsRequest = HttpRequest.newBuilder()
                .uri(new URI(groupsUrl))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", JSON_CONTENT_TYPE)
                .GET()
                .build();

        HttpResponse<String> groupsResponse = httpClient.send(groupsRequest, HttpResponse.BodyHandlers.ofString());

        if (groupsResponse.statusCode() == 200) {
            List<Map<String, Object>> groups = objectMapper.readValue(groupsResponse.body(),
                    new TypeReference<List<Map<String, Object>>>() {});

            List<String> groupNames = new ArrayList<>();
            for (Map<String, Object> group : groups) {
                String groupName = (String) group.get("name");
                if (groupName != null) groupNames.add(groupName);
                // Keycloak은 path("/DevTeam")도 내려줍니다. name만 쓰면 "/DevTeam" 비교가 실패할 수 있어 같이 보관합니다.
                String groupPath = (String) group.get("path");
                if (groupPath != null) groupNames.add(groupPath);
            }
            return groupNames;
        }

        return new ArrayList<>();
    }

    @Override
    public boolean hasScope(String userId, String scope) throws Exception {
        return getUserScopes(userId).contains(scope);
    }

    @Override
    public boolean isInGroup(String userId, String groupName) throws Exception {
        List<String> groups = getUserGroups(userId);
        if (groups.contains(groupName)) return true;

        // "/DevTeam" vs "DevTeam" 형태 차이 보정
        String normalized = normalizeGroupName(groupName);
        if (normalized != null) {
            for (String g : groups) {
                if (normalized.equals(normalizeGroupName(g))) return true;
            }
        }
        return false;
    }
}
