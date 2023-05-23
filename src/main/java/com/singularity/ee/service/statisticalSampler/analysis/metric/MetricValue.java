package com.singularity.ee.service.statisticalSampler.analysis.metric;

public class MetricValue {
    public long startTimeInMillis, occurrences, current, min, max, count, sum, value;
    public boolean useRange;
    public double standardDeviation;
}
