package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.commonservices.metricgeneration.IMetricPostProcessor;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.MetricsForTimeslice;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.RawMetrics;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.AgentRawMetricData;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.MetricAggregatorType;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.controller.api.dto.RawMetricValue;

import java.util.ArrayList;
import java.util.List;

public class StatMetricPostProcessor implements IMetricPostProcessor {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.StatMetricPostProcessor");

    private final AgentNodeProperties agentNodeProperties;

    public StatMetricPostProcessor(AgentNodeProperties agentNodeProperties ) {
        this.agentNodeProperties = agentNodeProperties;
        logger.debug("Initialized new Statistics Metric Post Processor");
    }

    @Override
    public MetricsForTimeslice process(MetricsForTimeslice metricsForTimeslice) {
        if( agentNodeProperties.isEnabled() ) { //the plugin is enabled
            logger.trace(String.format("isEnabled? %s IsMetricThrottled? %s", agentNodeProperties.isEnabled(), agentNodeProperties.isMetricThrottled()));
            List<AgentRawMetricData> metricDataList = new ArrayList<>();
            RawMetrics rawMetrics = metricsForTimeslice.getMetrics();
            if (agentNodeProperties.isMetricThrottled()) { //the plugin is enabled, and the node is not sending metrics
                for (AgentRawMetricData agentRawMetricData : rawMetrics.getRegisteredMetrics()) {
                    if(agentRawMetricData.getMetricIdentifier().getName().startsWith("Agent")
                            || agentRawMetricData.getMetricIdentifier().getMetricAggregatorType().equals(MetricAggregatorType.AVAILABILITY)) {
                        metricDataList.add(agentRawMetricData);
                        logger.trace(String.format("Adding metric %s to reporter", agentRawMetricData.getMetricIdentifier().toString()));
                    } else {
                        logger.trace(String.format("Dropping metric %s from reporter", agentRawMetricData.getMetricIdentifier().toString()));
                    }
                }
                metricsForTimeslice.setMetrics( new RawMetrics(metricDataList.toArray(new AgentRawMetricData[0]), rawMetrics.getUnregisteredMetrics()));
            } else { //the plugin is enabled, but this node is sending data
                for (AgentRawMetricData agentRawMetricData : rawMetrics.getRegisteredMetrics()) {
                    switch (agentRawMetricData.getMetricIdentifier().getMetricAggregatorType()) {
                        case SUM:   {
                            if( !agentRawMetricData.getMetricIdentifier().getName().startsWith("Agent")) { //apply extrapolation to all Summations except Agent| metrics
                                agentRawMetricData.setMetricValue( extrapolateSummation(agentRawMetricData.getMetricValue()) );
                                logger.trace(String.format("Summation metric %s being altered", agentRawMetricData.getMetricIdentifier().toString()));
                            } //after this fall through to default
                        }
                        default: {
                            metricDataList.add(agentRawMetricData);
                            break;
                        }
                    }
                }
                metricsForTimeslice.setMetrics( new RawMetrics(metricDataList.toArray(new AgentRawMetricData[0]), rawMetrics.getUnregisteredMetrics()));
            }
        }
        return metricsForTimeslice;
    }

    private RawMetricValue extrapolateSummation(RawMetricValue metricValue) {
        return new RawMetricValue(doMath(metricValue.getSum()), metricValue.getCount(), metricValue.getMin(), metricValue.getMax(), doMath(metricValue.getCurrent()) );
    }

    private long doMath(long l) { return l*100/agentNodeProperties.getEnabledPercentage(); }
}
