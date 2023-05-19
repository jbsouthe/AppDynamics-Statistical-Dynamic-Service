package com.singularity.ee.service.statisticalSampler;


import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.AMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.MetricAggregatorType;
import com.singularity.ee.controller.api.dto.RawMetricValue;
import com.singularity.ee.util.javaspecific.atomic.AgentAtomicLongImpl;
import com.singularity.ee.util.spi.IAgentAtomicLong;

public class ExtrapolatedSumMetricAggregator extends AMetricAggregator {

    private final IAgentAtomicLong sum = new AgentAtomicLongImpl(0L);
    private final IAgentAtomicLong lastAggregatedValue = new AgentAtomicLongImpl(0L);
    private AgentNodeProperties agentNodeProperties;

    public ExtrapolatedSumMetricAggregator(AgentNodeProperties agentNodeProperties ) {
        this.agentNodeProperties=agentNodeProperties;
    }

    public MetricAggregatorType getType() {
        return MetricAggregatorType.SUM;
    }

    protected void _report(long value) {
        this.setAsChanged();
        if( agentNodeProperties.isEnabled() ) {
            int percent = agentNodeProperties.getEnabledPercentage();
            value *= 100/agentNodeProperties.getEnabledPercentage();
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
