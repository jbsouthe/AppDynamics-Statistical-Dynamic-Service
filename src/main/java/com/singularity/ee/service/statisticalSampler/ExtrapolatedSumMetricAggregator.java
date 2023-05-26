package com.singularity.ee.service.statisticalSampler;


import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.AMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.MetricAggregatorType;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.controller.api.dto.RawMetricValue;
import com.singularity.ee.util.javaspecific.atomic.AgentAtomicLongImpl;
import com.singularity.ee.util.spi.IAgentAtomicLong;

public class ExtrapolatedSumMetricAggregator extends AMetricAggregator {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.ExtrapolatedSumMetricAggregator");

    private final IAgentAtomicLong sum = new AgentAtomicLongImpl(0L);
    private final IAgentAtomicLong lastAggregatedValue = new AgentAtomicLongImpl(0L);
    private AgentNodeProperties agentNodeProperties;
    private String metricName;

    public ExtrapolatedSumMetricAggregator(String metricName, AgentNodeProperties agentNodeProperties ) {
        this.agentNodeProperties=agentNodeProperties;
        this.metricName=metricName;
        logger.info(String.format("Createing new Extrapolated Aggregator for Summation metric '%s'",metricName));
    }

    public MetricAggregatorType getType() {
        return MetricAggregatorType.SUM;
    }

    protected void _report(long value) {
        this.setAsChanged();
        if( agentNodeProperties.isEnabled() ) {
            long orig=value;
            int percent = agentNodeProperties.getEnabledPercentage();
            value *= 100/agentNodeProperties.getEnabledPercentage();
            logger.debug(String.format("Extrapolating metric '%s' value from '%d' to '%d' with factor of %d", this.metricName, orig, value, 100/percent));
        }
        this.sum.addAndGet(value);
    }

    public long getCurrentSum() {
        return this.sum.get();
    }

    public long getLastAggregatedValue() {
        return this.lastAggregatedValue.get();
    }

    public void resetLastAggregateValue() {
        this.lastAggregatedValue.set(0L);
    }

    public RawMetricValue aggregate() {
        this.setAsUnchanged();
        long sum = this.sum.getAndSet(0L);
        this.lastAggregatedValue.set(sum);
        RawMetricValue val = new RawMetricValue();
        val.setSum(sum);
        val.setCurrent(sum);
        val.setMax(sum);
        val.setMin(sum);
        val.setCount(1L);
        return val;
    }
}
