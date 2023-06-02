package com.singularity.ee.service.statisticalSampler.aggregator;

import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.AMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.MetricAggregatorType;
import com.singularity.ee.controller.api.dto.RawMetricValue;
import com.singularity.ee.service.statisticalSampler.AgentNodeProperties;
import com.singularity.ee.util.javaspecific.atomic.AgentAtomicLongImpl;
import com.singularity.ee.util.spi.IAgentAtomicLong;

public class ManagedObservationForeverIncreasingMetricAggregator extends AMetricAggregator {
    private long deltaValue;
    private long lastReportedValue;
    private boolean firstTime = true;
    private final IAgentAtomicLong lastAggregatedValue = new AgentAtomicLongImpl(0L);
    private final Object aggregatorLock = new Object();
    private AgentNodeProperties agentNodeProperties;

    public ManagedObservationForeverIncreasingMetricAggregator( AgentNodeProperties agentNodeProperties) {
        this.agentNodeProperties=agentNodeProperties;
    }

    public MetricAggregatorType getType() {
        return MetricAggregatorType.OBSERVATION_FOREVERINCREASING;
    }

    protected void _report(long value) {
        if( agentNodeProperties.isEnabled() && agentNodeProperties.isMetricThrottled() ) {
            return; //do nothing, do not record this metric and let it idle
        }
        synchronized(this.aggregatorLock) {
            if (this.firstTime) {
                this.firstTime = false;
                this.lastReportedValue = value;
            } else if (value < this.lastReportedValue) {
                this.lastReportedValue = value;
                this.deltaValue = 0L;
                this.setAsUnchanged();
            } else {
                this.deltaValue = value - this.lastReportedValue;
                this.lastReportedValue = value;
                this.setAsChanged();
            }

        }
    }

    public long getLastAggregatedValue() {
        return this.lastAggregatedValue.get();
    }

    public void resetLastAggregateValue() {
        this.lastAggregatedValue.set(0L);
    }

    public RawMetricValue aggregate() {
        long value;
        synchronized(this.aggregatorLock) {
            this.setAsUnchanged();
            value = this.deltaValue;
        }

        this.lastAggregatedValue.set(value);
        RawMetricValue val = new RawMetricValue();
        val.setSum(value);
        val.setCurrent(value);
        val.setMax(value);
        val.setMin(value);
        val.setCount(1L);
        return val;
    }

    public void resetReportedIntervals() {
        this.firstTime = true;
        super.resetReportedIntervals();
    }
}
