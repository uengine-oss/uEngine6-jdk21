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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;
import org.uengine.five.ProcessServiceApplication;
import org.uengine.five.Streams;
import org.uengine.five.events.PythonQueued;
import org.uengine.five.events.RPAParams;
import org.uengine.kernel.IActivityEventQueue;
import org.uengine.kernel.ParameterContext;
import org.uengine.kernel.ProcessInstance;
import org.uengine.kernel.ProcessVariable;
import org.uengine.kernel.ReceiveActivity;

public class RPAActivity extends ReceiveActivity implements IActivityEventQueue {

    @Override
    protected void executeActivity(ProcessInstance instance) throws Exception {
        // TODO Auto-generated method stub
        super.executeActivity(instance);

        // TODO: python 명령을 보내는 부분 -> kafka event publish
        List<String> variablesMap = new ArrayList<>();
        ParameterContext[] parameterContexts = getParameters();
        for(ParameterContext parameterContext : parameterContexts) {
            String key = parameterContext.getArgument().getText();
            variablesMap.add(key + ":" + instance.get(key));
        }

        String[] arr = new String[variablesMap.size()];
        queue(instance.getInstanceId(), getTracingTag(), 3, variablesMap.toArray(arr));
        // ParameterContext[] params = getParameters();

        // String[] envStrings = {"PROCESS_ID=process2", "KAFKA=host.docker.internal",
        // "TOPIC=rpa-24"};
        // HostConfig hostConfig = HostConfig.newHostConfig() 
        // .withBinds(Bind.parse("D:\\docker\\process2:/app/server/process2"));
        // CreateContainerCmdImpl createContainerCmd = (CreateContainerCmdImpl)
        // dockerClient.createContainerCmd("sanghoon01/rpa-python:v2").withEnv(envStrings).withHostConfig(hostConfig);
        // CreateContainerResponse container = createContainerCmd.exec();
        // dockerClient.startContainerCmd(container.getId()).exec();

        // Thread.sleep(2000);
        // dockerConfig.killDocker();
        // sendMessage("rpa-24", "process2", "{\"idx\": \"1\",\"methods\":
        // \"open_browser\", \"keyword\": \"Browser\", \"params\": {\"url\":
        // \"http://rpachallenge.com/\", \"alias\": \"test\", \"browser\":\"chrome\",
        // \"options\":
        // \"add_argument(\'--headless\');add_argument(\'--no-sandbox\');add_argument(\'--single-process\')\"}}");
    }

    @Override
    protected void onReceive(ProcessInstance instance, Object payload) throws Exception {
        // TODO Auto-generated method stub
        super.onReceive(instance, payload);
        System.out.println(payload);
        // ....
    }

    ProcessVariable argument;
    ProcessVariable[] arguments;

    public ProcessVariable getArgument() {
        return argument;
    }

    public void setArgument(ProcessVariable argument) {
        this.argument = argument;
    }

    @Override
    public void queue(String instanceId, String tracingTag, int retryingCount, String[] additionalParameters) {
    
        // TODO Auto-generated method stub
        Streams streams = ProcessServiceApplication.getApplicationContext().getBean(Streams.class);
        Map<String, String> parameters = parseParams(additionalParameters);
        PythonQueued pythonQueued = new PythonQueued();
        pythonQueued.setResultReturn(false);
        pythonQueued.setKeywords(parameters.get("keywords"));
        pythonQueued.setMethods(parameters.get("methods"));

        RPAParams params = new RPAParams();
        params.setUrl(parameters.get("url"));
        params.setBrowser(parameters.get("browser"));
        params.setOptions(parameters.get("options"));
        // instance.get
        pythonQueued.setParams(params);
        // pythonQueued.setActivityInfo(new ActivityInfo());
        // pythonQueued.getActivityInfo().setInstanceId(instanceId);
        // pythonQueued.getActivityInfo().setTracingTag(tracingTag);

        MessageChannel messageChannel = streams.outboundPython();
        messageChannel.send(MessageBuilder
                .withPayload(pythonQueued)
                .setHeader("instanceId", instanceId)
                .setHeader("tracingTag", tracingTag)
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                .build());
    }
    public Map<String, String> parseParams(String[] additionalParameters) {
        Map<String, String> result = new HashMap<String, String>();
        List<String> parameterList = new ArrayList<>(Arrays.asList(additionalParameters));
        Iterator listIter = parameterList.iterator();
        while(listIter.hasNext()) {
            String keyVal = (String) listIter.next();
            String[] split = keyVal.split(":", 2);
            result.put(split[0], split[1]);
        }
        return result;
    }
}

*/
