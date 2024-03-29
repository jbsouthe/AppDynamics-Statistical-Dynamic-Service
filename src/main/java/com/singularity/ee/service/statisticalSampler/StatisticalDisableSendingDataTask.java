package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.appagent.kernel.ServiceComponent;
import com.singularity.ee.agent.appagent.kernel.spi.IDynamicService;
import com.singularity.ee.agent.appagent.kernel.spi.IServiceContext;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.util.javaspecific.threads.IAgentRunnable;

import java.util.Map;

public class StatisticalDisableSendingDataTask implements IAgentRunnable {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.StatisticalDisableSendingDataTask");
    private IDynamicService agentService;
    private AgentNodeProperties agentNodeProperties;
    private ServiceComponent serviceComponent;
    private IServiceContext serviceContext;
    private long lastDeterminationTimestamp=0, lastMetricAggregatorRegistration=0;
    private boolean isEnabled;

    public StatisticalDisableSendingDataTask(IDynamicService agentService, AgentNodeProperties agentNodeProperties, ServiceComponent serviceComponent, IServiceContext iServiceContext) {
        this.agentNodeProperties=agentNodeProperties;
        this.agentService=agentService;
        this.serviceComponent=serviceComponent;
        this.serviceContext=iServiceContext;
        isEnabled=false;
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
        logger.trace(String.format("Task Running this.isEnabled=%s agentNodeProperties.isEnabled()=%s",this.isEnabled, agentNodeProperties.isEnabled()));
        if( this.isEnabled && !agentNodeProperties.isEnabled() ) { // this has just been disabled, so let the metrics and events flow
            enableEverything();
            this.isEnabled=false; // reset the trigger
            return;
        }
        logger.debug(String.format("agentNodeProperties.isEnabled() = %s this.lastDeterminationTimestamp = %d seconds until reassessment = %d",
                agentNodeProperties.isEnabled(), this.lastDeterminationTimestamp,
                ( System.currentTimeMillis() - (this.lastDeterminationTimestamp + (agentNodeProperties.getDecisionDuration()*60000) ) )/-1000));
        if( !agentNodeProperties.isEnabled() ||
                System.currentTimeMillis() < this.lastDeterminationTimestamp + (agentNodeProperties.getDecisionDuration()*60000) )
            return; //only run this every 15 minutes, ish
        this.isEnabled=true;
        //1. get configured percentage of nodes that are enabled to send data
        Integer percentageOfNodesSendingData = agentNodeProperties.getEnabledPercentage();
        int r = (int) (Math.random() *100);
        if( r > percentageOfNodesSendingData ) { //if r > 10% (the large number
            sendInfoEvent("This Agent WILL NOT be sending data, it is randomly selected to reduce metrics and events to the controller r="+r);
            //serviceComponent.getMetricHandler().getMetricService().hotDisable(); //disable all metrics
            agentNodeProperties.setMetricThrottled(true);
            if( agentNodeProperties.isMaxEventsSet() ) {
                int newMaxEvents = agentNodeProperties.getMaxEvents();
                if( newMaxEvents > 0 ) {
                    serviceComponent.getEventHandler().getEventService().setMaxEventSize(newMaxEvents);
                } else { //just turn it off
                    serviceComponent.getEventHandler().getEventService().hotDisable(); //disable all events
                }
                agentNodeProperties.setEventThrottled(true);
            } else {
                sendInfoEvent("max events is not being adjusted because node property agent.statisticalSampler.maxEvents is not set [0-100]");
            }
        } else {//else r <= 10%; so enable everything
            sendInfoEvent("This Agent WILL be sending data, it is randomly selected to enable sending metrics and events to the controller r=" + r);
            enableEverything();
        }
        this.lastDeterminationTimestamp = System.currentTimeMillis();
    }

    public void enableEverything() {
        serviceComponent.getEventHandler().getEventService().hotEnable(); //enable all events again :)
        serviceComponent.getEventHandler().getEventService().setMaxEventSize(agentNodeProperties.getHoldMaxEvents());
        agentNodeProperties.setEventThrottled(false);
        agentNodeProperties.setMetricThrottled(false);
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
