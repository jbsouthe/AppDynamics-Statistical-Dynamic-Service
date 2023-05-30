package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.appagent.kernel.ServiceComponent;
import com.singularity.ee.agent.appagent.kernel.spi.IDynamicService;
import com.singularity.ee.agent.appagent.kernel.spi.IServiceContext;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.AppMetricAggregatorFactory;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.SumMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.boot.IMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.*;
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
        agentNodeProperties.setHoldMaxEvents( ReflectionHelper.getMaxEvents(serviceComponent.getEventHandler().getEventService()) );
        isEnabled=false;
        registerExtrapolationSumAggregators();
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
        logger.debug(String.format("Task Running this.isEnabled=%s agentNodeProperties.isEnabled()=%s",this.isEnabled, agentNodeProperties.isEnabled()));
        if( this.isEnabled && !agentNodeProperties.isEnabled() ) { // this has just been disabled, so let the metrics and events flow
            enableEverything();
            this.isEnabled=false; // reset the trigger
            return;
        }
        logger.debug(String.format("agentNodeProperties.isEnabled()=%s this.lastDeterminationTimestamp=%d", agentNodeProperties.isEnabled(),this.lastDeterminationTimestamp));
        if( !agentNodeProperties.isEnabled() ||
                System.currentTimeMillis() < this.lastDeterminationTimestamp + (agentNodeProperties.getDecisionDuration()*60000) )
            return; //only run this every 15 minutes, ish
        logger.info("Running the task to check if this node will be sending metrics or disabling that functionality");
        this.isEnabled=true;
        //1. get configured percentage of nodes that are enabled to send data
        Integer percentageOfNodesSendingData = agentNodeProperties.getEnabledPercentage();
        int r = (int) (Math.random() *100);
        if( r > percentageOfNodesSendingData ) { //if r > 10% (the large number
            sendInfoEvent("This Agent WILL NOT be sending data, it is randomly selected to reduce metrics and events to the controller r="+r);
            serviceComponent.getMetricHandler().getMetricService().hotDisable(); //disable all metrics
            agentNodeProperties.setMetricThrottled(true);
            if( agentNodeProperties.isMaxEventsSet() ) {
                int newMaxEvents = agentNodeProperties.getMaxEvents();
                if( newMaxEvents > 0 ) {
                    ReflectionHelper.setMaxEvents(serviceComponent.getEventHandler().getEventService(), newMaxEvents);
                } else { //just turn it off
                    serviceComponent.getEventHandler().getEventService().hotDisable(); //disable all events
                }
                agentNodeProperties.setEventThrottled(true);
            } else {
                sendInfoEvent("max events is not being adjusted because node property agent.statisticalSampler.maxEvents is not set [0-100]");
            }
        } else {//else r <= 10%; so enable everything
            sendInfoEvent("This Agent WILL be sending data, it is randomly selected to enable sending metrics and events to the controller r=" + r);
            registerExtrapolationSumAggregators();
            enableEverything();
        }
        this.lastDeterminationTimestamp = System.currentTimeMillis();
    }

    public void enableEverything() {
        serviceComponent.getMetricHandler().getMetricService().hotEnable(); //enable all metrics again :)
        serviceComponent.getEventHandler().getEventService().hotEnable(); //enable all events again :)
        ReflectionHelper.setMaxEvents( serviceComponent.getEventHandler().getEventService(), agentNodeProperties.getHoldMaxEvents() );
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

    private void registerOriginalSumAggregators() {
        IMetricReporterFactory iMetricReporterFactory = serviceComponent.getMetricHandler().getAggregatorFactory();
        for ( AgentRawMetricIdentifier agentRawMetricIdentifier : iMetricReporterFactory.getRegisteredMetrics() ) {
            if( agentRawMetricIdentifier.getMetricAggregatorType().equals(MetricAggregatorType.SUM))
                iMetricReporterFactory.registerAggregator(agentRawMetricIdentifier, new SumMetricAggregator());
        }
    }

    private void registerExtrapolationSumAggregators() {
        AppMetricAggregatorFactory iMetricReporterFactory = (AppMetricAggregatorFactory) serviceComponent.getMetricHandler().getAggregatorFactory();
        for ( AgentRawMetricIdentifier agentRawMetricIdentifier : iMetricReporterFactory.getRegisteredMetrics() ) {
            if( agentRawMetricIdentifier.getMetricAggregatorType().equals(MetricAggregatorType.SUM)
                && !agentRawMetricIdentifier.getName().startsWith("Agent|")) {
                try {
                    if( !(iMetricReporterFactory.getAggregator(agentRawMetricIdentifier) instanceof ExtrapolatedSumMetricAggregator) ) {
                        registerNewMetricAggregator(iMetricReporterFactory, agentRawMetricIdentifier);
                    }
                } catch (MetricUnavailableException metricUnavailableException) {
                    logger.warn(String.format("Error while checking existing aggregator for metric '%s', Exception: %s", agentRawMetricIdentifier.getName(), metricUnavailableException));
                    registerNewMetricAggregator(iMetricReporterFactory, agentRawMetricIdentifier);
                }
            }
        }
    }

    private void registerNewMetricAggregator(AppMetricAggregatorFactory iMetricReporterFactory, AgentRawMetricIdentifier agentRawMetricIdentifier) {
        logger.debug(String.format("Updating MetricAggregator for '%s' of type '%s'", agentRawMetricIdentifier.getName(), agentRawMetricIdentifier.getMetricAggregatorType().name()));
        iMetricReporterFactory.unregisterAggregator(agentRawMetricIdentifier);
        try {
            IMetricAggregator metricAggregator = iMetricReporterFactory.safeGetAggregator(agentRawMetricIdentifier);
            iMetricReporterFactory.purge(metricAggregator);
            logger.debug(String.format("before metric aggregator for %s is %s", agentRawMetricIdentifier.getName(), metricAggregator.toString()));
        } catch (Exception e) {
            logger.debug("before exception: "+ e,e);
        }
        IMetricAggregator extrapolatedSumMetricAggregator = iMetricReporterFactory.registerAggregator(agentRawMetricIdentifier, new ExtrapolatedSumMetricAggregator(agentRawMetricIdentifier.getName(), agentNodeProperties));
        logger.debug(String.format("during metric aggregator for %s is %s", agentRawMetricIdentifier.getName(), extrapolatedSumMetricAggregator.toString()));
        try {
            IMetricAggregator metricAggregator = iMetricReporterFactory.safeGetAggregator(agentRawMetricIdentifier);
            logger.debug(String.format("after metric aggregator for %s is %s", agentRawMetricIdentifier.getName(), metricAggregator.toString()));
        } catch (Exception e) {
            logger.debug("after exception: "+ e,e);
        }
    }
}
