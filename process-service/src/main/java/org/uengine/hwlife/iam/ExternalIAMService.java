package org.uengine.hwlife.iam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.uengine.contexts.UserContext;
import org.uengine.five.service.IAMService;

/**
 * 외부 IAM(ESB·사내 디렉터리 등) 연동 구현체.
 *
 * <p>그룹과 권한은 서로 종속되지 않으며, UI에서 각각 독립적으로 선택합니다.
 * 담당자 조회 시 전달된 파라미터 조합에 따라 결과가 결정됩니다.</p>
 *
 * <p>외부 시스템 연동 방식(ESB, REST API 등)이 확정되면 본 클래스에서 매핑을 채웁니다.</p>
 *
 * <p>애플리케이션 기동 시
 * {@code IAMServiceFactory.register("external", ExternalIAMService.getDefault())} 로 등록합니다.</p>
 */
public class ExternalIAMService implements IAMService {

    private static ExternalIAMService defaultService;

    private ExternalIAMService() {
    }

    public static synchronized ExternalIAMService getDefault() {
        if (defaultService == null) {
            defaultService = new ExternalIAMService();
        }
        return defaultService;
    }

    @Override
    public String getProviderId() {
        return "external";
    }

    @Override
    public Map<String, Object> getUserById(String userId) throws Exception {
        return findUserByEmployeeNo(userId)
                .map(this::toUserMap)
                .orElse(null);
    }

    @Override
    public List<String> getUsersByGroup(String groupName) throws Exception {
        return toUserIds(getAssignees(groupName, null));
    }

    @Override
    public List<String> getUsersByRole(String roleName) throws Exception {
        return toUserIds(getAssignees(null, roleName));
    }

    @Override
    public List<String> getUserScopes(String userId) throws Exception {
        return new ArrayList<>();
    }

    @Override
    public List<String> getUserGroups(String userId) throws Exception {
        return new ArrayList<>();
    }

    /**
     * 그룹 정보 목록 (코드·이름). {@link GroupInfoDto}는 공통 응답 형태로 재사용합니다.
     */
    public List<GroupInfoDto> getGroups() {
        return new ArrayList<>();
    }

    /**
     * 권한 정보 목록 (코드·이름). 그룹과 무관하게 전체 권한을 조회합니다.
     * {@link RoleInfoDto}는 공통 응답 형태로 재사용합니다.
     */
    public List<RoleInfoDto> getPermissions() {
        return new ArrayList<>();
    }

    /**
     * 그룹·권한 조건에 따른 담당자 목록.
     *
     * <ul>
     *   <li>{@code groupCode}만 있으면 해당 그룹 담당자</li>
     *   <li>{@code permissionCode}만 있으면 해당 권한 담당자</li>
     *   <li>둘 다 있으면 두 조건을 모두 만족하는 담당자</li>
     *   <li>둘 다 없으면 빈 목록</li>
     * </ul>
     */
    public List<UserContext> getAssignees(String groupCode, String permissionCode) {
        if (!hasText(groupCode) && !hasText(permissionCode)) {
            return List.of();
        }
        return new ArrayList<>();
    }

    /**
     * 사번으로 담당자(사용자) 단건 조회.
     */
    public Optional<UserContext> findUserByEmployeeNo(String employeeNo) {
        return Optional.empty();
    }

    private static List<String> toUserIds(List<UserContext> assignees) {
        List<String> userIds = new ArrayList<>();
        for (UserContext assignee : assignees) {
            if (assignee != null && hasText(assignee.getUserId())) {
                userIds.add(assignee.getUserId());
            }
        }
        return userIds;
    }

    private Map<String, Object> toUserMap(UserContext user) {
        return Map.of("userId", user.getUserId());
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
