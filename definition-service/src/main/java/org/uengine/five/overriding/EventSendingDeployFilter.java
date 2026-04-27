package org.uengine.five.overriding;

import org.uengine.five.DefinitionServiceApplication;
import org.uengine.five.events.DefinitionDeployed;
import org.uengine.five.messaging.EventPublisher;
import org.uengine.kernel.Activity;
import org.uengine.kernel.DeployFilter;
import org.uengine.kernel.ProcessDefinition;
import org.uengine.kernel.bpmn.CatchingRestMessageEvent;
import org.uengine.processmanager.ProcessTransactionContext;

import java.util.List;

/**
 * Created by uengine on 2018. 1. 5..
 */
public class EventSendingDeployFilter implements DeployFilter {



    @Override
    public void beforeDeploy(ProcessDefinition definition, ProcessTransactionContext tc, String path, boolean isNew) throws Exception {

        DefinitionDeployed definitionDeployed = new DefinitionDeployed();
        definitionDeployed.setDefintionId(path);

        EventPublisher eventPublisher = DefinitionServiceApplication.getApplicationContext().getBean(EventPublisher.class);
        eventPublisher.send("bpm-out", definitionDeployed);


        List<Activity> startActivities = definition.getStartActivities();

        if(startActivities != null){
            for(Activity activity : startActivities){

                if(activity instanceof CatchingRestMessageEvent){

                    CatchingRestMessageEvent catchingMessageEvent = (CatchingRestMessageEvent) activity;

    //                ServiceEndpointRepository serviceEndpointRepository = MetaworksRemoteService.getComponent(ServiceEndpointRepository.class);
    //
    //                ServiceEndpointEntity serviceEndpointEntity = new ServiceEndpointEntity();
    //
    //                serviceEndpointEntity.setPath(catchingMessageEvent.getServicePath());
    //                serviceEndpointEntity.setCorrelationKey(catchingMessageEvent.getCorrelationKey());
    //                serviceEndpointEntity.setDefId(folder);
    //
    //                serviceEndpointRepository.save(serviceEndpointEntity);

                }

            }
        }


    }
}
