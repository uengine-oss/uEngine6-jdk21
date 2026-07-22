package org.uengine.hwlife.iam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.uengine.contexts.UserContext;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.service.IAMService;
import org.uengine.hwlife.esbclient.client.EsbClient;
import org.uengine.hwlife.iam.dto.FncgOrgInfo;
import org.uengine.hwlife.iam.dto.FncgRoleInfo;

/**
 * 외부 IAM(ESB·사내 디렉터리 등) 연동 구현체.
 *
 * <p>그룹과 권한은 서로 종속되지 않으며, UI에서 각각 독립적으로 선택합니다.
 * 담당자 조회 시 전달된 파라미터 조합에 따라 결과가 결정됩니다.</p>
 *
 * <p>Spring Bean이 아닌 싱글톤 팩토리 메서드({@link #getDefault()})로 인스턴스를 관리합니다.
 * {@link org.uengine.five.service.KeycloakIAMService}와 동일 패턴이며, ESB 호출에 필요한
 * {@link EsbClient}만 ApplicationContext에서 조회합니다.</p>
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

    /**
     * KeycloakIAMService의 HttpClient에 해당하는 의존성.
     * EsbClient는 Spring 빈이므로 런타임에 ApplicationContext에서 조회한다.
     * (생성자/getDefault 안이 아닌 업무 메서드에서만 호출)
     */
    private EsbClient esbClient() {
        return ProcessServiceApplication.getApplicationContext().getBean(EsbClient.class);
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
        return new ArrayList<>();
    }

    @Override
    public List<String> getUsersByRole(String roleName) throws Exception {
        return new ArrayList<>();
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
     * 그룹 정보 목록 (코드·이름). {@link FncgOrgInfo}는 공통 응답 형태로 재사용합니다.
     */
    public List<FncgOrgInfo> getGroups() {
        return new ArrayList<>();
    }

    /**
     * 권한 정보 목록 (코드·이름). 그룹과 무관하게 전체 권한을 조회합니다.
     * {@link FncgRoleInfo}는 공통 응답 형태로 재사용합니다.
     */
    public List<FncgRoleInfo> getRoles() {
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
