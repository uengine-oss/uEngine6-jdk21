package org.uengine.five.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.uengine.contexts.UserContext;
import org.uengine.five.dto.GroupInfoDto;
import org.uengine.five.dto.RoleInfoDto;

/**
 * HFC(ESB) 조직·권한·담당자 조회 및 IAM 연동을 담당하는 서비스.
 *
 * <p>그룹과 권한은 서로 종속되지 않으며, UI에서 각각 독립적으로 선택합니다.
 * 담당자 조회 시 전달된 파라미터 조합에 따라 결과가 결정됩니다.</p>
 *
 * <p>ESB 호출이 확정되면 본 클래스에서 매핑을 채웁니다.</p>
 */
@Service
public class HfcIAMService {

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

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
