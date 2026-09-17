package org.uengine.hwlife.overriding;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uengine.five.overriding.IAMRoleResolutionContext;
import org.uengine.kernel.DirectRoleResolutionContext;
import org.uengine.kernel.DynamicRoleMappingContext;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.IContainsMapping;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.Role;
import org.uengine.kernel.RoleMapping;
import org.uengine.kernel.RoleResolutionContext;

/**
 * 기존 {@link RoleResolutionContext}(IAM / Direct / RuleBased 등)를 감싸는 Decorator.
 *
 * <p>처리 순서:</p>
 * <ol>
 *   <li>{@code base.getActualMapping()} 으로 base 기본값 mapping 생성</li>
 *   <li>instance 변수({@code bindings})가 있으면 해당 필드만 덮어쓰기</li>
 *   <li>{@code assignType} 재계산</li>
 * </ol>
 *
 * <p>BPMN lane JSON 예시:</p>
 * <pre>
 * "roleResolutionContext": {
 *   "_type": "org.uengine.hwlife.overriding.BoundRoleResolutionContext",
 *   "base": {
 *     "_type": "org.uengine.five.overriding.IAMRoleResolutionContext",
 *     "scope": "engineer",
 *     "groupName": "SW팀"
 *   },
 *   "bindings": {
 *     "scope": "TroubleScope",
 *     "groupName": "OrgCode"
 *   }
 * }
 * </pre>
 */
public class BoundRoleResolutionContext extends RoleResolutionContext
        implements IContainsMapping, DynamicRoleMappingContext {

    private static final long serialVersionUID = GlobalContext.SERIALIZATION_UID;
    private static final Logger log = LoggerFactory.getLogger(BoundRoleResolutionContext.class);

    /** 정적 기본값을 가진 실제 배분 전략. */
    private RoleResolutionContext base;

    /**
     * 속성명 → 프로세스 변수 키.
     * 예: scope → TroubleScope, groupName → OrgCode, endpoint → EmpNo
     */
    private LinkedHashMap<String, String> bindings = new LinkedHashMap<>();

    public RoleResolutionContext getBase() {
        return base;
    }

    public void setBase(RoleResolutionContext base) {
        this.base = base;
    }

    public LinkedHashMap<String, String> getBindings() {
        return bindings;
    }

    public void setBindings(LinkedHashMap<String, String> bindings) {
        this.bindings = bindings != null ? bindings : new LinkedHashMap<>();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public RoleMapping getActualMapping(ProcessDefinition pd, ProcessInstance instance,
                                        String tracingTag, Map options) throws Exception {
        return buildBoundMapping(pd, instance, tracingTag, options);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public RoleMapping resolveRoleMapping(ProcessDefinition pd, ProcessInstance instance,
                                          String tracingTag, RoleMapping currentMapping,
                                          Map options) throws Exception {
        if (base == null) {
            throw new IllegalStateException("BoundRoleResolutionContext: base RoleResolutionContext is required");
        }
        // bindings 없거나 instance 변수가 하나도 없으면: 기존 mapping 유지(클레임), 없으면 base
        LinkedHashMap<String, String> values = readAvailableBindingValues(instance, tracingTag);
        if (bindings == null || bindings.isEmpty() || values.isEmpty()) {
            return currentMapping != null ? currentMapping
                    : resolveBaseMapping(pd, instance, tracingTag, options);
        }

        try {
            RoleMapping candidate = buildBoundMapping(pd, instance, tracingTag, options);
            if (candidate == null) {
                log.warn("[BpmAssignment] Bound assignment empty; falling back to base. tracingTag={}, values={}",
                        tracingTag, values);
                RoleMapping fallback = resolveBaseMapping(pd, instance, tracingTag, options);
                applyAssignType(fallback);
                return preferExistingIfSame(currentMapping, fallback);
            }
            return preferExistingIfSame(currentMapping, candidate);
        } catch (Exception e) {
            log.warn("[BpmAssignment] Bound assignment lookup failed; falling back to base. tracingTag={}, values={}",
                    tracingTag, values, e);
            RoleMapping fallback = resolveBaseMapping(pd, instance, tracingTag, options);
            applyAssignType(fallback);
            return preferExistingIfSame(currentMapping, fallback);
        }
    }

    /**
     * 1) base.getActualMapping() → 2) instance 변수로 필드 덮어쓰기 → 3) assignType 재계산
     */
    @SuppressWarnings("rawtypes")
    private RoleMapping buildBoundMapping(ProcessDefinition pd, ProcessInstance instance,
                                         String tracingTag, Map options) throws Exception {
        if (base == null) {
            throw new IllegalStateException("BoundRoleResolutionContext: base RoleResolutionContext is required");
        }
        RoleMapping mapping = base.getActualMapping(pd, instance, tracingTag, options);
        applyInstanceOverrides(mapping, instance, tracingTag);
        applyAssignType(mapping);
        if (log.isDebugEnabled()) {
            log.debug("[BpmAssignment] Bound mapping built. groupName={}, scope={}, endpoint={}, assignType={}",
                    mapping != null ? mapping.getGroupName() : null,
                    mapping != null ? mapping.getScope() : null,
                    mapping != null ? mapping.getEndpoint() : null,
                    mapping != null ? mapping.getAssignType() : null);
        }
        return mapping;
    }

    /**
     * base mapping 위에 instance 변수 값을 필드 단위로 덮어쓴다.
     * 변수에 값이 있는 필드만 변경하고, 없으면 base mapping 값을 유지한다.
     */
    private void applyInstanceOverrides(RoleMapping mapping, ProcessInstance instance, String tracingTag)
            throws Exception {
        if (mapping == null) {
            return;
        }
        LinkedHashMap<String, String> values = readAvailableBindingValues(instance, tracingTag);
        if (values.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String property = entry.getKey();
            String value = entry.getValue();
            if ("endpoint".equals(property)) {
                mapping.setEndpoint(value);
            } else if ("groupName".equals(property)) {
                mapping.setGroupName(value);
            } else if ("scope".equals(property)) {
                mapping.setScope(value);
            }
        }
    }

    @Override
    public boolean containsMapping(ProcessInstance instance, RoleMapping testingRoleMapping)
            throws Exception {
        if (base == null) {
            throw new IllegalStateException("BoundRoleResolutionContext: base RoleResolutionContext is required");
        }
        RoleMapping actualMapping = buildBoundMapping(null, instance, null, Collections.emptyMap());
        if (base instanceof IContainsMapping) {
            // IAM containsMapping 은 context 의 scope/groupName 을 보므로,
            // 최종 mapping 값으로 clone 한 context 에 반영 후 위임한다.
            RoleResolutionContext checkContext = contextWithMappingFields(actualMapping);
            return ((IContainsMapping) checkContext).containsMapping(instance, testingRoleMapping);
        }
        return hasSameEndpoint(actualMapping, testingRoleMapping)
                || hasSameResourceName(actualMapping, testingRoleMapping);
    }

    /** containsMapping 용: base clone 후 최종 mapping 필드를 반영. */
    private RoleResolutionContext contextWithMappingFields(RoleMapping mapping) {
        RoleResolutionContext clone = cloneContext(base);
        if (mapping == null) {
            return clone;
        }
        if (clone instanceof IAMRoleResolutionContext) {
            IAMRoleResolutionContext iam = (IAMRoleResolutionContext) clone;
            if (isNotEmpty(mapping.getGroupName())) {
                iam.setGroupName(mapping.getGroupName());
            }
            if (isNotEmpty(mapping.getScope())) {
                iam.setScope(mapping.getScope());
            }
        } else if (clone instanceof DirectRoleResolutionContext) {
            DirectRoleResolutionContext direct = (DirectRoleResolutionContext) clone;
            if (isNotEmpty(mapping.getEndpoint())) {
                direct.setEndpoint(mapping.getEndpoint());
            }
            if (isNotEmpty(mapping.getResourceName())) {
                direct.setResourceName(mapping.getResourceName());
            }
        }
        return clone;
    }

    @Override
    public String getDisplayName() {
        if (base == null) {
            return "Bound Role Resolution (no base)";
        }
        String baseName = base.getDisplayName();
        if (bindings == null || bindings.isEmpty()) {
            return baseName;
        }
        return baseName + " [bound:" + String.join(",", bindings.keySet()) + "]";
    }

    @Override
    public String getName() {
        String n = super.getName();
        if (n != null && !n.isEmpty()) {
            return n;
        }
        return base != null ? base.getName() : "Bound Role Resolution";
    }

    /** 배분 기준이 같으면 기존 mapping 을 재사용해 불필요한 putRoleMapping 을 막는다. */
    private static RoleMapping preferExistingIfSame(RoleMapping current, RoleMapping candidate) {
        if (candidate == null) {
            return current;
        }
        if (current != null && hasSameAssignmentCriteria(current, candidate)) {
            return current;
        }
        return candidate;
    }

    @SuppressWarnings("rawtypes")
    private RoleMapping resolveBaseMapping(ProcessDefinition pd, ProcessInstance instance,
                                           String tracingTag, Map options) throws Exception {
        RoleMapping mapping = base.getActualMapping(pd, instance, tracingTag, options);
        applyAssignType(mapping);
        return mapping;
    }

    private LinkedHashMap<String, String> readAvailableBindingValues(
            ProcessInstance instance, String tracingTag) throws Exception {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (bindings == null || bindings.isEmpty()) {
            return values;
        }
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            String property = entry.getKey();
            String varKey = entry.getValue();
            if (!isSupportedBinding(property) || !isNotEmpty(varKey)) {
                continue;
            }
            String value = readVar(instance, tracingTag, varKey);
            if (value == null) {
                continue;
            }
            values.put(property, value);
        }
        return values;
    }

    private static boolean hasSameAssignmentCriteria(RoleMapping current, RoleMapping candidate) {
        if (isNotEmpty(candidate.getEndpoint())) {
            return candidate.getEndpoint().equals(current.getEndpoint());
        }
        return same(candidate.getGroupName(), current.getGroupName())
                && same(candidate.getScope(), current.getScope())
                && candidate.getAssignType() == current.getAssignType();
    }

    private static boolean isSupportedBinding(String property) {
        return "endpoint".equals(property) || "groupName".equals(property) || "scope".equals(property);
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    /**
     * 최종 mapping 필드 기준으로 assignType 설정.
     * <ul>
     *   <li>{@link DirectRoleResolutionContext} base + endpoint → {@link Role#ASSIGNTYPE_USER} (0)</li>
     *   <li>{@link IAMRoleResolutionContext} + groupName 만 → {@link Role#ASSIGNTYPE_GROUP} (3)</li>
     *   <li>{@link IAMRoleResolutionContext} + scope 만 → {@link Role#ASSIGNTYPE_ROLE} (4)</li>
     *   <li>{@link IAMRoleResolutionContext} + groupName + scope → {@link Role#ASSIGNTYPE_GROUP_ROLE} (5)</li>
     * </ul>
     */
    private void applyAssignType(RoleMapping mapping) {
        if (mapping == null) {
            return;
        }
        boolean hasGroup = isNotEmpty(mapping.getGroupName());
        boolean hasScope = isNotEmpty(mapping.getScope());

        if (base instanceof DirectRoleResolutionContext) {
            if (isNotEmpty(mapping.getEndpoint())) {
                mapping.setAssignType(Role.ASSIGNTYPE_USER);
            }
            return;
        }

        if (base instanceof IAMRoleResolutionContext) {
            if (hasGroup && hasScope) {
                mapping.setAssignType(Role.ASSIGNTYPE_GROUP_ROLE);
            } else if (hasScope) {
                mapping.setAssignType(Role.ASSIGNTYPE_ROLE);
            } else if (hasGroup) {
                mapping.setAssignType(Role.ASSIGNTYPE_GROUP);
            }
        }
    }

    private static String readVar(ProcessInstance instance, String tracingTag, String varKey)
            throws Exception {
        if (instance == null || !isNotEmpty(varKey)) {
            return null;
        }
        Object v = instance.get("", varKey);
        if (v == null && tracingTag != null) {
            v = instance.getProperty(tracingTag, varKey);
        }
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static RoleResolutionContext cloneContext(RoleResolutionContext source) {
        try {
            ByteArrayOutputStream bao = new ByteArrayOutputStream();
            ObjectOutputStream ow = new ObjectOutputStream(bao);
            ow.writeObject(source);
            ow.close();
            ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(bao.toByteArray()));
            return (RoleResolutionContext) oi.readObject();
        } catch (Exception e) {
            throw new RuntimeException("BoundRoleResolutionContext: failed to clone base " +
                    source.getClass().getName(), e);
        }
    }

    private static boolean isNotEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static boolean hasSameEndpoint(RoleMapping actualMapping, RoleMapping testingRoleMapping) {
        if (actualMapping == null || testingRoleMapping == null) {
            return false;
        }
        String actualEndpoint = actualMapping.getEndpoint();
        String testingEndpoint = testingRoleMapping.getEndpoint();
        return isNotEmpty(actualEndpoint) && actualEndpoint.equals(testingEndpoint);
    }

    private static boolean hasSameResourceName(RoleMapping actualMapping, RoleMapping testingRoleMapping) {
        if (actualMapping == null || testingRoleMapping == null) {
            return false;
        }
        String actualResourceName = actualMapping.getResourceName();
        String testingResourceName = testingRoleMapping.getResourceName();
        return isNotEmpty(actualResourceName) && actualResourceName.equals(testingResourceName);
    }
}
