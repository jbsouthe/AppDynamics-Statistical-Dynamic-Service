package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.boot.IMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.boot.IMetricListener;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;

public class ExtrapolatedSumMetricListener implements IMetricListener {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.ExtrapolatedSumMetricListener");

    IMetricAggregator metricAggregator;
    AgentNodeProperties agentNodeProperties;

    public ExtrapolatedSumMetricListener(IMetricAggregator metricAggregator, AgentNodeProperties agentNodeProperties ) {
        this.metricAggregator = metricAggregator;
        this.agentNodeProperties = agentNodeProperties;
    }

    @Override
    public void report(long value) {
        if( agentNodeProperties.isEnabled() ) {
            long orig=value;
            int percent = agentNodeProperties.getEnabledPercentage();
            value *= 100/agentNodeProperties.getEnabledPercentage();
            logger.info(String.format("Listener Extrapolating metric '%s' value from '%d' to '%d' with factor of %d", orig, value, 100/percent));
        }
    }
}
