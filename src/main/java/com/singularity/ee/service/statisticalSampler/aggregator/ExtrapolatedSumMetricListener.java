package com.singularity.ee.service.statisticalSampler.aggregator;

import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.boot.IMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.boot.IMetricListener;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.service.statisticalSampler.AgentNodeProperties;

public class ExtrapolatedSumMetricListener implements IMetricListener {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.ExtrapolatedSumMetricListener");

    IMetricAggregator metricAggregator;
    AgentNodeProperties agentNodeProperties;
    String name = "UNKNOWN";

    public ExtrapolatedSumMetricListener(String name, IMetricAggregator metricAggregator, AgentNodeProperties agentNodeProperties ) {
        this.metricAggregator = metricAggregator;
        this.agentNodeProperties = agentNodeProperties;
        this.name = name;
    }

    @Override
    public void report(long value) {
        if( agentNodeProperties.isEnabled() && !agentNodeProperties.isMetricThrottled() ) {
            long orig=value;
            int percent = agentNodeProperties.getEnabledPercentage();
            value *= 100/agentNodeProperties.getEnabledPercentage();
            logger.debug(String.format("Listener Extrapolating metric '%s' value from '%d' to '%d' with factor of %d", this.name, orig, value, 100/percent));
            value -= orig; //remove the original value, because we are adding this to the already collected value
            //ReflectionHelper.addReportMetric( this.metricAggregator, value);
            this.metricAggregator.reportBypassBackgroundProcessing(value);
        }
    }
}
