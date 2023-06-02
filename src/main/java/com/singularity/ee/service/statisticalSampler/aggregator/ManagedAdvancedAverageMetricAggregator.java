package com.singularity.ee.service.statisticalSampler.aggregator;

import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.AMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.AdvancedAverageMetricAggregator;
import com.singularity.ee.service.statisticalSampler.AgentNodeProperties;

public class ManagedAdvancedAverageMetricAggregator extends AdvancedAverageMetricAggregator {
    private AgentNodeProperties agentNodeProperties;
    public ManagedAdvancedAverageMetricAggregator(AgentNodeProperties agentNodeProperties) {
        super();
        this.agentNodeProperties=agentNodeProperties;
    }

    public void _report(long value) {
        if( agentNodeProperties.isEnabled() && agentNodeProperties.isMetricThrottled() ) {
            return; //do nothing, do not record this metric and let it idle
        }
        super._report(value);
    }

    public void report(long value, long aCount, long aMin, long aMax) {
        if( agentNodeProperties.isEnabled() && agentNodeProperties.isMetricThrottled() ) {
            return; //do nothing, do not record this metric and let it idle
        }
        super.report(value, aCount, aMin, aMax);
    }
}
