package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.appagent.kernel.AgentRestRequestFactory;
import com.singularity.ee.agent.appagent.kernel.ServiceComponent;
import com.singularity.ee.agent.appagent.kernel.spi.IDynamicService;
import com.singularity.ee.agent.appagent.kernel.spi.IServiceContext;
import com.singularity.ee.agent.commonservices.eventgeneration.IEventGenerationService;
import com.singularity.ee.agent.commonservices.metricgeneration.MetricGenerationService;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.AppMetricAggregatorFactory;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.SumMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.boot.IMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.*;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.service.statisticalSampler.aggregator.ExtrapolatedSumMetricAggregator;
import com.singularity.ee.service.statisticalSampler.aggregator.LoggingObserver;
import com.singularity.ee.service.statisticalSampler.aggregator.StatMetricAggregatorFactory;
import com.singularity.ee.util.javaspecific.threads.IAgentRunnable;
import com.singularity.ee.util.logging.ILogger;
import com.singularity.ee.util.system.SystemUtilsTranslateable;

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
        //registerExtrapolationSumAggregators(); //disabled until i can figure out how to do this without causing the summation metrics to stop updating
        AppMetricAggregatorFactory iMetricReporterFactory = (AppMetricAggregatorFactory) serviceComponent.getMetricHandler().getAggregatorFactory();
        serviceComponent.getConfigManager().getIConfigChannel().addMetricAggregatorFactory(
                new StatMetricAggregatorFactory((ILogger) this.logger, serviceComponent.getEventHandler().getEventService(), SystemUtilsTranslateable.getIntProperty("appdynamics.agent.maxMetrics", 5000), agentNodeProperties)
        );
        iMetricReporterFactory.clearAllAggregators();
        serviceComponent.getMetricHandler().getMetricService().addObserverForMetricAggregator(new LoggingObserver(logger));
        MetricGenerationService mgs = serviceComponent.getMetricHandler().getMetricService();
        ReflectionHelper.updateMetricReporter( mgs, agentNodeProperties);
        /*
        for ( AgentRawMetricIdentifier agentRawMetricIdentifier : iMetricReporterFactory.getRegisteredMetrics() ) {
            logger.debug(String.format("Updating MetricAggregator for '%s' of type '%s'", agentRawMetricIdentifier.getName(), agentRawMetricIdentifier.getMetricAggregatorType().name()));
            iMetricReporterFactory.unregisterAggregator(agentRawMetricIdentifier);
            try {
                IMetricAggregator metricAggregator = iMetricReporterFactory.safeGetAggregator(agentRawMetricIdentifier);
                iMetricReporterFactory.purge(metricAggregator);
                IMetricAggregator currentMetricAggregator = serviceComponent.getMetricHandler().getAggregatorFactory().safeGetAggregator(agentRawMetricIdentifier);
                logger.debug(String.format("metric aggregator for %s was %s and now is %s", agentRawMetricIdentifier.getName(), metricAggregator.toString(), currentMetricAggregator));
            } catch (Exception e) {
                logger.debug("before exception: "+ e,e);
            }
        }

         */
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
            enableEverything();
        }
        this.lastDeterminationTimestamp = System.currentTimeMillis();
    }

    public void enableEverything() {
        //serviceComponent.getMetricHandler().getMetricService().hotEnable(); //enable all metrics again :)
        //registerExtrapolationSumAggregators(); //disabled until i can figure out how to do this without causing the summation metrics to stop updating
        serviceComponent.getEventHandler().getEventService().hotEnable(); //enable all events again :)
        ReflectionHelper.setMaxEvents( serviceComponent.getEventHandler().getEventService(), agentNodeProperties.getHoldMaxEvents() );
        agentNodeProperties.setEventThrottled(false);
        agentNodeProperties.setMetricThrottled(false);
        serviceComponent.getConfigManager().getIConfigChannel().getConfigurationContext().setStartedAgentReregistration();
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
                /*
                //this technique uses a listener to add the extrapolated metrics to an enabled aggregator
                try {
                    IMetricAggregator metricAggregator = iMetricReporterFactory.safeGetAggregator(agentRawMetricIdentifier);
                    logger.debug(String.format("Adding a Metric Listener to %s which is of type %s", metricAggregator, metricAggregator.getType()));
                    metricAggregator.setMetricListener( new ExtrapolatedSumMetricListener(agentRawMetricIdentifier.getName(), metricAggregator, agentNodeProperties));
                } catch (Exception e) {
                    logger.warn(String.format("Ugh: Exception: %s", e), e);
                }
                */
                //this technique registers a new aggregator to handle the extrapolated data
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
        IMetricAggregator extrapolatedSumMetricAggregator = iMetricReporterFactory.registerAggregator(agentRawMetricIdentifier, new ExtrapolatedSumMetricAggregator(agentNodeProperties));
        logger.debug(String.format("after metric aggregator for %s is %s", agentRawMetricIdentifier.getName(), extrapolatedSumMetricAggregator.toString()));
    }
}
