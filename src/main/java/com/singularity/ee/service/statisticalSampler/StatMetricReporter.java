package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.commonservices.metricgeneration.IMetricGenerationService;
import com.singularity.ee.agent.commonservices.metricgeneration.MetricReporter;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.MetricRegistrationException;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.MetricSendException;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.MetricsForTimeslice;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.AgentRawMetricData;
import com.singularity.ee.agent.debug.AgentDebugEventSenderProxy;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.controller.api.constants.AgentType;
import com.singularity.ee.util.collections.CollectionHelper;
import com.singularity.ee.util.collections.bounded.SharedBoundedConcurrentLinkedQueue;
import com.singularity.ee.util.javaspecific.threads.IAgentRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StatMetricReporter extends MetricReporter {
    private final AgentType agentType;
    private final IMetricGenerationService mgs;
    private final ConcurrentLinkedQueue<MetricsForTimeslice> unprocessedMetrics = new SharedBoundedConcurrentLinkedQueue(500);
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.StatMetricReporter");


    public void collectMetrics(MetricsForTimeslice metrics) {
        if (this.mgs.getLogger().isTraceEnabled()) {
            this.mgs.getLogger().trace("collecting metrics");
        }

        while(CollectionHelper.queueSize(this.unprocessedMetrics) >= this.mgs.getMaxPublishQueueLength()) {
            MetricsForTimeslice removed = (MetricsForTimeslice)this.unprocessedMetrics.remove();
            this.mgs.getLogger().warn("Metric Reporter Queue full. Dropping metrics.");
            switch (this.agentType) {
                case APP_AGENT:
                    AgentDebugEventSenderProxy.getInstance().sendMetricDebugEventDropped(removed);
            }
        }

        this.unprocessedMetrics.add(metrics);
    }

    void clearQueue() {
        this.unprocessedMetrics.clear();
    }
    private AgentNodeProperties agentNodeProperties;
    public StatMetricReporter(IMetricGenerationService mgs, AgentType agentType, AgentNodeProperties agentNodeProperties) {
        super(mgs, agentType);
        this.mgs = mgs;
        this.agentType = agentType;
        this.agentNodeProperties=agentNodeProperties;
    }

    public void run() {
        logger.debug(String.format("Stat Metric Reporter is running isEnabled: %s is Metric Throttled: %S",agentNodeProperties.isEnabled(), agentNodeProperties.isMetricThrottled()));
        try {
            if (!this.mgs.getSubscriber().isInitialized()) {
                return;
            }
            if( agentNodeProperties.isEnabled() && agentNodeProperties.isMetricThrottled() ) {
                return; //do nothing, do not record this metric and let it idle
            }

            for(MetricsForTimeslice metricsForTimeslice = (MetricsForTimeslice)this.unprocessedMetrics.peek(); metricsForTimeslice != null; metricsForTimeslice = (MetricsForTimeslice)this.unprocessedMetrics.peek()) {
                if (!this.mgs.isMetricDataRequest()) {
                    if (this.mgs.getLogger().isDebugEnabled()) {
                        this.mgs.getLogger().debug("Metrics request disabled, unprocessed metric queue is drained but will not be sent to controller");
                    }

                    this.unprocessedMetrics.poll();
                    return;
                }

                if (this.mgs.getSubscriber() != null) {
                    try {
                        List<AgentRawMetricData> registeredMetrics = new ArrayList();
                        List<AgentRawMetricData> verifiedUnregisteredMetrics = this.mgs.separateUnregisteredMetrics(metricsForTimeslice, registeredMetrics);
                        List<AgentRawMetricData> successfullyRegisteredMetric = this.mgs.registerMetrics(verifiedUnregisteredMetrics);
                        CollectionHelper.addAll(registeredMetrics, CollectionHelper.asList(metricsForTimeslice.getMetrics().getRegisteredMetrics()));
                        CollectionHelper.addAll(registeredMetrics, successfullyRegisteredMetric);
                        metricsForTimeslice.getMetrics().setRegisteredMetrics((AgentRawMetricData[])CollectionHelper.collectionToArray(registeredMetrics, new AgentRawMetricData[registeredMetrics.size()]));
                        metricsForTimeslice.getMetrics().setUnregisteredMetrics(AgentRawMetricData.EMPTY_ARRAY);
                    } catch (MetricRegistrationException var5) {
                        this.mgs.getLogger().error("Error registering metrics", var5);
                    }


                    this.mgs.getSubscriber().publish(metricsForTimeslice);
                    this.setChanged();
                    this.notifyObservers(metricsForTimeslice);
                    this.unprocessedMetrics.poll();
                } else {
                    this.unprocessedMetrics.poll();
                }
            }
        } catch (MetricSendException var6) {
            this.mgs.getLogger().error("Error sending metrics - will requeue for later transmission", var6);
        } catch (Throwable var7) {
            this.mgs.getLogger().error("Error reporting metrics", var7);
        }

    }
}
