/*
 * [비활성화됨] 이 파일은 컴파일되지 않는 상태로 저장소에 남아 있었다.
 *
 *   - org.uengine.five.events.RPAParams          : 저장소 어디에도 존재하지 않음
 *   - org.uengine.five.Streams#outboundPython()  : 존재하지 않음
 *
 * 그동안은 target/classes 에 남아 있던 옛 .class 를 maven 증분 컴파일이 재사용해
 * 드러나지 않았을 뿐이다. 커널 인터페이스 변경으로 모듈 전체 재컴파일이 트리거되면서
 * 표면화되어, 빌드를 살리기 위해 본문을 주석 처리한다.
 *
 * 되살리려면 위 두 심볼을 복구한 뒤 아래 주석을 해제하면 된다.
 * 참조하는 코드는 저장소에 없다 (확인 완료).
 */

/*
package org.uengine.five.rpa;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.uengine.five.ProcessServiceApplication;
import org.uengine.kernel.DefaultActivity;
import org.uengine.kernel.ParameterContext;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.UEngineException;

/**
 * BPMN 상에서 RPA(robotframework) 실행을 담당하는 액티비티.
 *
 * <p>모델링: bpmn:serviceTask 의 uengine:properties json 에
 * {@code "_type":"org.uengine.five.rpa.RPAActivity"} 로 지정한다.
 *
 * <p>실행: executeActivity 에서 RPA Job 을 생성(큐잉)만 하고 fireComplete 를 호출하지
 * 않으므로 액티비티는 RUNNING 상태로 대기한다. 서버 워커(Docker) 또는 클라이언트
 * 에이전트(트레이 앱)가 Job 을 폴링-실행 후 결과를 REST 로 보고하면
 * {@link RpaJobService} 가 {@link #onJobResult(ProcessInstance, Map)} 를 호출해
 * 결과값을 ProcessVariable 로 매핑하고 fireComplete 로 프로세스를 진행시킨다.
 */
public class RPAActivity extends DefaultActivity {

    private static final long serialVersionUID = org.uengine.kernel.GlobalContext.SERIALIZATION_UID;

    public static final String MODE_SERVER = "server";
    public static final String MODE_CLIENT = "client";

    /** server: Docker 워커가 실행. client: 사용자 PC 의 트레이 에이전트가 실행. */
    String executionType = MODE_SERVER;

    /** robotframework(.robot) 스크립트 원문. */
    String robotScript;

    /** client 모드에서 Job 을 가져갈 사용자 endpoint (예: hong@uengine.org). */
    String targetUser;

    /** server 모드에서 사용할 도커 이미지 표시용(워커 배포 참고 정보). */
    String dockerImage;

    /** Job 실행 제한 시간(초). 초과 시 실패 처리. */
    int timeoutSeconds = 600;

    /**
     * 변수 매핑. direction=in : 프로세스 변수(variable) 값을 argument 이름의 robot
     * variable 로 전달. direction=out : RPA 결과 맵의 argument 키 값을 프로세스
     * 변수(variable)에 할당.
     */
    ParameterContext[] parameters;

    public RPAActivity() {
        super("RPA");
    }

    @Override
    protected void executeActivity(ProcessInstance instance) throws Exception {

        Map<String, Object> inputs = new LinkedHashMap<>();
        if (parameters != null) {
            for (ParameterContext parameter : parameters) {
                String direction = parameter.getDirection();
                if (direction == null || ParameterContext.DIRECTION_IN.equals(direction)
                        || ParameterContext.DIRECTION_INOUT.equals(direction)) {
                    String argumentName = parameter.getArgument() != null ? parameter.getArgument().getText() : null;
                    String variableName = parameter.getVariable() != null ? parameter.getVariable().getName() : null;
                    if (variableName == null)
                        variableName = argumentName;
                    if (argumentName == null)
                        argumentName = variableName;
                    if (argumentName == null)
                        continue;

                    inputs.put(argumentName, instance.getBeanProperty(variableName));
                }
            }
        }

        RpaJobService rpaJobService = ProcessServiceApplication.getApplicationContext()
                .getBean(RpaJobService.class);

        rpaJobService.createJob(this, instance, inputs);

        // fireComplete 를 호출하지 않는다 — Job 결과가 도착할 때까지 RUNNING 상태 유지.
    }

    /**
     * RPA Job 성공 결과 반영. RpaJobService 가 @ProcessTransactional 안에서 호출한다.
     */
    public void onJobResult(ProcessInstance instance, Map<String, Object> result) throws Exception {

        if (result == null)
            result = new HashMap<>();

        boolean mapped = false;
        if (parameters != null) {
            for (ParameterContext parameter : parameters) {
                String direction = parameter.getDirection();
                if (ParameterContext.DIRECTION_OUT.equals(direction)
                        || ParameterContext.DIRECTION_INOUT.equals(direction)) {

                    String resultKey = parameter.getArgument() != null ? parameter.getArgument().getText() : null;
                    String variableName = parameter.getVariable() != null ? parameter.getVariable().getName() : null;
                    if (variableName == null)
                        variableName = resultKey;
                    if (resultKey == null)
                        resultKey = variableName;
                    if (variableName == null)
                        continue;

                    // "[Arguments].name" 형태로 저장된 변수명 보정
                    if (variableName.startsWith("[Arguments]."))
                        variableName = variableName.substring("[Arguments].".length());

                    Object value = result.get(resultKey);
                    instance.setBeanProperty(variableName, (Serializable) value);
                    mapped = true;
                }
            }
        }

        // 명시적 out 매핑이 없으면 결과 키를 동일 이름의 프로세스 변수에 그대로 할당
        if (!mapped) {
            for (Map.Entry<String, Object> entry : result.entrySet()) {
                if (entry.getValue() instanceof Serializable || entry.getValue() == null) {
                    instance.setBeanProperty(entry.getKey(), (Serializable) entry.getValue());
                }
            }
        }

        fireComplete(instance);
    }

    /**
     * RPA Job 실패 반영 — 액티비티를 fault 상태로 만든다.
     */
    public void onJobFailed(ProcessInstance instance, String errorMessage) throws Exception {
        fireFault(instance, new UEngineException(
                "RPA job failed for activity [" + getName() + "(" + getTracingTag() + ")]: " + errorMessage));
    }

    public String getExecutionType() {
        return executionType;
    }

    public void setExecutionType(String executionType) {
        this.executionType = executionType;
    }

    public String getRobotScript() {
        return robotScript;
    }

    public void setRobotScript(String robotScript) {
        this.robotScript = robotScript;
    }

    public String getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public ParameterContext[] getParameters() {
        return parameters;
    }

    public void setParameters(ParameterContext[] parameters) {
        this.parameters = parameters;
    }
}

*/
