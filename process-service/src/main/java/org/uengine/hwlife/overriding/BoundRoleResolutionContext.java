package org.uengine.hwlife.overriding;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
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
import org.uengine.five.service.IAMService;
import org.uengine.five.service.IAMServiceFactory;
import org.uengine.kernel.DirectRoleResolutionContext;
import org.uengine.kernel.DynamicRoleMappingContext;
import org.uengine.kernel.GlobalContext;
import org.uengine.kernel.IContainsMapping;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.RoleMapping;
import org.uengine.kernel.RoleResolutionContext;

/**
 * 기존 {@link RoleResolutionContext}(IAM / Direct / RuleBased 등)를 감싸,
 * 프로세스 변수에서 동적으로 덮어쓴 뒤 위임하는 Decorator.
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
 *
 * <p>해석 규칙: binding 변수에 값이 있으면 override, 없으면 {@code base} 정적 기본값 유지.
 * 정의 객체는 mutate 하지 않고 clone 후 적용한다.</p>
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
        RoleResolutionContext resolved = resolve(instance, tracingTag);
        RoleMapping mapping = resolved.getActualMapping(pd, instance, tracingTag, options);
        applyResultBindings(mapping, instance, tracingTag);
        return mapping;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public RoleMapping resolveRoleMapping(ProcessDefinition pd, ProcessInstance instance,
                                          String tracingTag, RoleMapping currentMapping,
                                          Map options) throws Exception {
        if (base == null) {
            throw new IllegalStateException("BoundRoleResolutionContext: base RoleResolutionContext is required");
        }
        if (bindings == null || bindings.isEmpty()) {
            return currentMapping != null ? currentMapping : resolveBaseMapping(pd, instance, tracingTag, options);
        }

        LinkedHashMap<String, String> values = readAvailableBindingValues(instance, tracingTag);
        if (values == null || values.isEmpty()) {
            return currentMapping != null ? currentMapping : resolveBaseMapping(pd, instance, tracingTag, options);
        }

        try {
            RoleResolutionContext resolved = resolveWithValues(values, currentMapping);
            RoleMapping candidate = resolved.getActualMapping(pd, instance, tracingTag, options);
            if (candidate == null || !isValidAssignment(resolved, candidate)) {
                log.warn("[BpmAssignment] Bound assignment rejected; keeping inherited/default mapping. tracingTag={}, values={}",
                        tracingTag, values);
                return currentMapping != null ? currentMapping : resolveBaseMapping(pd, instance, tracingTag, options);
            }
            return currentMapping != null && hasSameAssignmentCriteria(currentMapping, candidate)
                    ? currentMapping
                    : candidate;
        } catch (Exception e) {
            log.warn("[BpmAssignment] Bound assignment lookup failed; keeping inherited/default mapping. tracingTag={}, values={}",
                    tracingTag, values, e);
            return currentMapping != null ? currentMapping : resolveBaseMapping(pd, instance, tracingTag, options);
        }
    }

    @Override
    public boolean containsMapping(ProcessInstance instance, RoleMapping testingRoleMapping)
            throws Exception {
        if (base == null) {
            throw new IllegalStateException("BoundRoleResolutionContext: base RoleResolutionContext is required");
        }
        RoleResolutionContext resolved = base;
        LinkedHashMap<String, String> values = readAvailableBindingValues(instance, null);
        if (values != null && !values.isEmpty()) {
            try {
                RoleResolutionContext candidateContext = resolveWithValues(values);
                RoleMapping candidate = candidateContext.getActualMapping(
                        null, instance, null, Collections.emptyMap());
                if (candidate != null && isValidAssignment(candidateContext, candidate)) {
                    resolved = candidateContext;
                }
            } catch (Exception ignored) {
                resolved = base;
            }
        }
        if (resolved instanceof IContainsMapping) {
            return ((IContainsMapping) resolved).containsMapping(instance, testingRoleMapping);
        }
        RoleMapping actualMapping = resolved.getActualMapping(null, instance, null, Collections.emptyMap());
        return hasSameEndpoint(actualMapping, testingRoleMapping)
                || hasSameResourceName(actualMapping, testingRoleMapping);
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

    /**
     * base 를 clone 한 뒤 bindings 로 속성을 override 한 인스턴스를 반환한다.
     * bindings 가 비어 있으면 base 원본을 그대로 반환(읽기 전용 사용 가정).
     */
    RoleResolutionContext resolve(ProcessInstance instance, String tracingTag) throws Exception {
        if (base == null) {
            throw new IllegalStateException("BoundRoleResolutionContext: base RoleResolutionContext is required");
        }
        if (bindings == null || bindings.isEmpty()) {
            return base;
        }

        RoleResolutionContext clone = cloneContext(base);
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            String property = entry.getKey();
            String varKey = entry.getValue();
            if ("endpoint".equals(property)) {
                continue;
            }
            if (!isNotEmpty(property) || !isNotEmpty(varKey)) {
                continue;
            }
            String override = readVar(instance, tracingTag, varKey);
            if (override != null) {
                setProperty(clone, property, override);
            }
        }
        return clone;
    }

    private RoleResolutionContext resolveWithValues(Map<String, String> values) throws Exception {
        return resolveWithValues(values, null);
    }

    private RoleResolutionContext resolveWithValues(Map<String, String> values, RoleMapping currentMapping) throws Exception {
        RoleResolutionContext clone = cloneContext(base);
        if (clone instanceof IAMRoleResolutionContext && currentMapping != null) {
            IAMRoleResolutionContext iam = (IAMRoleResolutionContext) clone;
            iam.setGroupName(currentMapping.getGroupName());
            iam.setScope(currentMapping.getScope());
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if ("endpoint".equals(entry.getKey()) && clone instanceof DirectRoleResolutionContext) {
                ((DirectRoleResolutionContext) clone).setEndpoint(entry.getValue());
            } else {
                setProperty(clone, entry.getKey(), entry.getValue());
            }
        }
        return clone;
    }

    @SuppressWarnings("rawtypes")
    private RoleMapping resolveBaseMapping(ProcessDefinition pd, ProcessInstance instance,
                                           String tracingTag, Map options) throws Exception {
        return base.getActualMapping(pd, instance, tracingTag, options);
    }

    private LinkedHashMap<String, String> readAvailableBindingValues(
            ProcessInstance instance, String tracingTag) throws Exception {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            if (!isSupportedBinding(entry.getKey()) || !isNotEmpty(entry.getValue())) {
                return null;
            }
            String value = readVar(instance, tracingTag, entry.getValue());
            if (value == null) {
                continue;
            }
            if (base instanceof IAMRoleResolutionContext) {
                try {
                    IAMService iam = getIamService();
                    if ("groupName".equals(entry.getKey()) && !iam.isValidGroup(value)) continue;
                    if ("scope".equals(entry.getKey()) && !iam.isValidRole(value)) continue;
                } catch (Exception e) {
                    log.warn("[BpmAssignment] Bound field lookup failed; retaining inherited field: {}",
                            entry.getKey(), e);
                    continue;
                }
            }
            values.put(entry.getKey(), value);
        }
        return values;
    }

    private boolean isValidAssignment(RoleResolutionContext resolved, RoleMapping mapping) throws Exception {
        IAMService iam = getIamService();
        if (resolved instanceof DirectRoleResolutionContext) {
            return isNotEmpty(mapping.getEndpoint()) && iam.isValidUser(mapping.getEndpoint());
        }
        if (resolved instanceof IAMRoleResolutionContext) {
            // Each supplied field was validated independently. An empty member
            // intersection must not undo a valid process-variable override.
            return true;
        }
        return true;
    }

    protected IAMService getIamService() {
        return IAMServiceFactory.getDefault();
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

    private void applyResultBindings(RoleMapping mapping, ProcessInstance instance, String tracingTag)
            throws Exception {
        if (mapping == null || bindings == null || bindings.isEmpty()) {
            return;
        }

        String endpoint = getBindingValue(instance, tracingTag, "endpoint");
        if (endpoint != null) {
            mapping.setEndpoint(endpoint);
        }
    }

    private String getBindingValue(ProcessInstance instance, String tracingTag, String propertyName)
            throws Exception {
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        String varKey = bindings.get(propertyName);
        if (!isNotEmpty(varKey)) {
            return null;
        }
        return readVar(instance, tracingTag, varKey);
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
        if (!(v instanceof String)) {
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

    private static void setProperty(Object target, String propertyName, String value) throws Exception {
        for (PropertyDescriptor pd : Introspector.getBeanInfo(target.getClass()).getPropertyDescriptors()) {
            if (propertyName.equals(pd.getName()) && pd.getWriteMethod() != null) {
                Class<?> type = pd.getWriteMethod().getParameterTypes()[0];
                if (type == String.class || type == Object.class) {
                    pd.getWriteMethod().invoke(target, value);
                    return;
                }
            }
        }
        String setter = "set" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        try {
            target.getClass().getMethod(setter, String.class).invoke(target, value);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "BoundRoleResolutionContext: no writable String property '" + propertyName +
                            "' on " + target.getClass().getName(), e);
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
