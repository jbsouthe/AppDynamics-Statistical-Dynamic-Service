package com.singularity.ee.service.statisticalSampler.aggregator;

import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.AMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.MetricAggregatorType;
import com.singularity.ee.controller.api.dto.RawMetricValue;
import com.singularity.ee.service.statisticalSampler.AgentNodeProperties;
import com.singularity.ee.util.javaspecific.atomic.AgentAtomicLongImpl;
import com.singularity.ee.util.spi.IAgentAtomicLong;

public class ManagedAverageMetricAggregator extends AMetricAggregator {
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max;
    private long current;
    private final IAgentAtomicLong lastAggregatedValue = new AgentAtomicLongImpl(0L);
    private AgentNodeProperties agentNodeProperties;

    public ManagedAverageMetricAggregator(AgentNodeProperties agentNodeProperties) {
        this.agentNodeProperties = agentNodeProperties;
    }

    public MetricAggregatorType getType() {
        return MetricAggregatorType.AVERAGE;
    }

    protected void _report(long value) {
        if( agentNodeProperties.isEnabled() && agentNodeProperties.isMetricThrottled() ) {
            return; //do nothing, do not record this metric and let it idle
        }
        this.setAsChanged();
        synchronized(this) {
            ++this.count;
            this.sum += value;
        }

        this.min = value < this.min ? value : this.min;
        this.max = value > this.max ? value : this.max;
        this.current = value;
    }

    public void report(long value, long aCount, long aMin, long aMax) {
        if( agentNodeProperties.isEnabled() && agentNodeProperties.isMetricThrottled() ) {
            return; //do nothing, do not record this metric and let it idle
        }
        this.setAsChanged();
        synchronized(this) {
            this.count += aCount;
            this.sum += value;
        }

        this.current = value;
        this.min = aMin < this.min ? aMin : this.min;
        this.max = aMax > this.max ? aMax : this.max;
    }

    public long getLastAggregatedValue() {
        return this.lastAggregatedValue.get();
    }

    public void resetLastAggregateValue() {
        this.lastAggregatedValue.set(0L);
    }

    public RawMetricValue aggregate() {
        long count;
        long sum;
        long min;
        long max;
        long current;
        synchronized(this) {
            this.setAsUnchanged();
            count = this.count;
            sum = this.sum;
            min = this.min == Long.MAX_VALUE ? 0L : this.min;
            max = this.max;
            current = this.current;
            this.count = 0L;
            this.sum = 0L;
            this.min = Long.MAX_VALUE;
            this.max = 0L;
            this.current = 0L;
        }

        long avgValue = count == 0L ? 0L : sum / count;
        this.lastAggregatedValue.set(avgValue);
        RawMetricValue val = new RawMetricValue();
        val.setSum(sum);
        val.setCurrent(current);
        val.setMax(max);
        val.setMin(min == Long.MAX_VALUE ? 0L : min);
        val.setCount(count);
        return val;
    }
}
