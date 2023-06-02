package com.singularity.ee.service.statisticalSampler.aggregator;

import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.AMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.MetricAggregatorType;
import com.singularity.ee.controller.api.dto.RawMetricValue;
import com.singularity.ee.service.statisticalSampler.AgentNodeProperties;
import com.singularity.ee.util.javaspecific.atomic.AgentAtomicLongImpl;
import com.singularity.ee.util.spi.IAgentAtomicLong;

public class ManagedObservationMetricAggregator extends AMetricAggregator {
    private final IAgentAtomicLong value = new AgentAtomicLongImpl(0L);
    private final IAgentAtomicLong lastAggregatedValue = new AgentAtomicLongImpl(0L);
    private AgentNodeProperties agentNodeProperties;

    public ManagedObservationMetricAggregator(AgentNodeProperties agentNodeProperties) {
        this.agentNodeProperties=agentNodeProperties;
    }

    public MetricAggregatorType getType() {
        return MetricAggregatorType.OBSERVATION;
    }

    protected void _report(long value) {
        if( agentNodeProperties.isEnabled() && agentNodeProperties.isMetricThrottled() ) {
            return; //do nothing, do not record this metric and let it idle
        }
        this.setAsChanged();
        this.value.set(value);
    }

    public long getLastAggregatedValue() {
        return this.lastAggregatedValue.get();
    }

    public void resetLastAggregateValue() {
        this.lastAggregatedValue.set(0L);
    }

    public RawMetricValue aggregate() {
        this.setAsUnchanged();
        long value = this.value.get();
        this.lastAggregatedValue.set(value);
        RawMetricValue val = new RawMetricValue();
        val.setSum(value);
        val.setCurrent(value);
        val.setMax(value);
        val.setMin(value);
        val.setCount(1L);
        return val;
    }
}
