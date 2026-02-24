package org.uengine.five.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import org.uengine.five.entity.WorklistEntity;
import org.uengine.kernel.Activity;
import org.uengine.kernel.HumanActivity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by uengine on 2017. 8. 22..
 * It is an "Associate" object that associates entity - ActivityEntity and value
 * object - desiredState and ResultPayload
 *
 * NOTE: RepresentationModel 상속을 제거함.
 * Spring Boot 3.x / HATEOAS 2.x에서 RepresentationModel 서브클래스에 대해
 * HAL Jackson 모듈이 자동 개입하여 Map&lt;String, Object&gt; 등 제네릭 프로퍼티를
 * 직렬화하지 못하고 누락시키는 문제가 있었음. 이 클래스는 HATEOAS 링크를
 * 사용하지 않으므로 일반 POJO로 변경하여 표준 Jackson BeanSerializer가 동작하도록 함.
 */
public class WorkItemResource { //extends RepresentationModel {

    WorklistEntity worklist;

    public WorklistEntity getWorklist() {
        return worklist;
    }

    public void setWorklist(WorklistEntity worklist) {
        this.worklist = worklist;
    }

    String execScope;

    public String getExecScope() {
        return execScope;
    }

    public void setExecScope(String execScope) {
        this.execScope = execScope;
    }

    Activity activity;

    /** 직렬화 제외: Activity는 커널 객체라 JSON 직렬화 시 순환참조/실패로 parameterValues 등 다른 필드 누락 유발 */
    @JsonIgnore
    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    /** HTTP 응답용: activity를 순환 참조 없이 안전한 필드만 Map으로 노출. EventSynchronization 포함. */
    @JsonProperty("activity")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Object> getActivitySummary() {
        if (activity == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", activity.getName());
        summary.put("tracingTag", activity.getTracingTag());
        if (activity.getEventSynchronization() != null) {
            summary.put("eventSynchronization", activity.getEventSynchronization());
        }
        if (activity instanceof HumanActivity) {
            String tool = ((HumanActivity) activity).getTool();
            if (tool != null) {
                summary.put("tool", tool);
            }
        }
        return summary;
    }

    private static void putIfNonNull(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    String desiredState;

    public String getDesiredState() {
        return desiredState;
    }

    public void setDesiredState(String desiredState) {
        this.desiredState = desiredState;
    }

    @JsonProperty("parameterValues")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    Map<String, Object> parameterValues;

    public Map<String, Object> getParameterValues() {
        return parameterValues;
    }

    public void setParameterValues(Map<String, Object> parameterValues) {
        this.parameterValues = parameterValues;
    }

}
