package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.appagent.kernel.ServiceComponent;
import com.singularity.ee.agent.appagent.kernel.spi.IDynamicService;
import com.singularity.ee.agent.appagent.kernel.spi.IServiceContext;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.util.javaspecific.threads.IAgentRunnable;
import com.singularity.ee.util.system.SystemUtilsTranslateable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

public class StatisticalDisableEventsSendingTask implements IAgentRunnable {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.ee.service.statisticalSampler.StatisticalDisableEventsSendingTask");
    private IDynamicService agentService;
    private AgentNodeProperties agentNodeProperties;
    private ServiceComponent serviceComponent;
    private IServiceContext serviceContext;

    public StatisticalDisableEventsSendingTask(IDynamicService agentService, AgentNodeProperties agentNodeProperties, ServiceComponent serviceComponent, IServiceContext iServiceContext) {
        this.agentNodeProperties=agentNodeProperties;
        this.agentService=agentService;
        this.serviceComponent=serviceComponent;
        this.serviceContext=iServiceContext;
        agentNodeProperties.setMaxEvents( ReflectionHelper.getMaxEvents(serviceComponent.getEventHandler().getEventService()) );
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        logger.info("Running the task to check if this node will be sending all events or disabling that functionality");
        //1. get configured percentage of nodes that are enabled to send data
        Integer percentageOfNodesSendingData = agentNodeProperties.getEnabledPercentage();
        int r = (int) (Math.random() *100);
        if( r > percentageOfNodesSendingData ) { //if r > 10% (the large number
            int newMaxEvents = agentNodeProperties.getMaxEvents();
            sendInfoEvent("This Agent WILL NOT be sending data, it is randomly selected to reduce events to the controller r="+r+" maxEvents set to "+ newMaxEvents);
            if( newMaxEvents > 0 ) {
                ReflectionHelper.setMaxEvents(serviceComponent.getEventHandler().getEventService(), newMaxEvents);
            } else { //just turn it off
                serviceComponent.getEventHandler().getEventService().hotDisable(); //disable all events
            }
            return;
        } //else r <= 10%; so continue
        sendInfoEvent("This Agent WILL be sending data, it is randomly selected to enable events to the controller r="+r);
        serviceComponent.getEventHandler().getEventService().hotEnable(); //enable all events again :)
        ReflectionHelper.setMaxEvents( serviceComponent.getEventHandler().getEventService(), agentNodeProperties.getHoldMaxEvents() );
    }

    private void sendInfoEvent(String message) {
        sendInfoEvent(message, MetaData.getAsMap());
    }

    private void sendInfoEvent(String message, Map map) {
        logger.info("Sending Custom INFO Event with message: "+ message);
        if( !map.containsKey("statisticalSampler-version") ) map.putAll(MetaData.getAsMap());
        serviceComponent.getEventHandler().publishInfoEvent(message, map);
    }

    
}
