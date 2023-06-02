package com.singularity.ee.service.statisticalSampler.aggregator;


import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.AMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.MetricAggregatorType;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.controller.api.dto.RawMetricValue;
import com.singularity.ee.service.statisticalSampler.AgentNodeProperties;
import com.singularity.ee.util.javaspecific.atomic.AgentAtomicLongImpl;
import com.singularity.ee.util.spi.IAgentAtomicLong;

public class ExtrapolatedSumMetricAggregator extends AMetricAggregator {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.ExtrapolatedSumMetricAggregator");

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
        logger.debug(String.format("metric recording value %d", value));
        this.setAsChanged();
        if( agentNodeProperties.isEnabled() ) {
            if( agentNodeProperties.isMetricThrottled() ) {
                return; //do nothing, do not record this metric and let it idle
            } //else fall through and record an extrapolated sum
            long orig=value;
            int percent = agentNodeProperties.getEnabledPercentage();
            value *= 100/agentNodeProperties.getEnabledPercentage();
            logger.info(String.format("Extrapolating metric value from '%d' to '%d' with factor of %d", orig, value, 100/percent));
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

    public String toString() {
        return String.format("%s %d%% enabled? %s", this.getClass().getName(), agentNodeProperties.getEnabledPercentage(), agentNodeProperties.isEnabled());
    }
}
