package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.appagent.kernel.ServiceComponent;
import com.singularity.ee.agent.appagent.kernel.spi.IDynamicService;
import com.singularity.ee.agent.appagent.kernel.spi.IServiceContext;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.util.javaspecific.threads.IAgentRunnable;

import java.util.Map;

public class StatisticalDisableMetricsSendingTask implements IAgentRunnable {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.ee.service.statisticalSampler.CheckForAgentUpgradeRequestTask");
    private IDynamicService agentService;
    private AgentNodeProperties agentNodeProperties;
    private ServiceComponent serviceComponent;
    private IServiceContext serviceContext;

    public StatisticalDisableMetricsSendingTask(IDynamicService agentService, AgentNodeProperties agentNodeProperties, ServiceComponent serviceComponent, IServiceContext iServiceContext) {
        this.agentNodeProperties=agentNodeProperties;
        this.agentService=agentService;
        this.serviceComponent=serviceComponent;
        this.serviceContext=iServiceContext;
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
        logger.info("Running the task to check if this node will be sending metrics or disabling that functionality");
        //1. get configured percentage of nodes that are enabled to send data
        Integer percentageOfNodesSendingData = agentNodeProperties.getEnabledPercentage();
        int r = (int) (Math.random() *100);
        if( r > percentageOfNodesSendingData ) {
            logger.info("This Agent WILL NOT be sending data, it is randomly selected to disable metrics to the controller r="+r);
            return;
        }
        logger.info("This Agent WILL be sending data, it is randomly selected to enable metrics to the controller r="+r);

    }

    private void sendInfoEvent(String message) {
        sendInfoEvent(message, MetaData.getAsMap());
    }

    private void sendInfoEvent(String message, Map map) {
        logger.info("Sending Custom INFO Event with message: "+ message);
        if( !map.containsKey("agentupdater-version") ) map.putAll(MetaData.getAsMap());
        serviceComponent.getEventHandler().publishInfoEvent(message, map);
    }
    
}
