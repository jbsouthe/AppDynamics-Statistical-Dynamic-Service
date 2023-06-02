package com.singularity.ee.service.statisticalSampler.aggregator;

import com.singularity.ee.agent.commonservices.config.ConnectivityEvent;
import com.singularity.ee.agent.commonservices.eventgeneration.IEventGenerationService;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.*;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.boot.IMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.RawMetrics;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.*;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.controller.api.constants.EventType;
import com.singularity.ee.controller.api.dto.RawMetricValue;
import com.singularity.ee.service.statisticalSampler.AgentNodeProperties;
import com.singularity.ee.util.collections.CollectionHelper;
import com.singularity.ee.util.javaspecific.atomic.AgentAtomicBooleanImpl;
import com.singularity.ee.util.javaspecific.collections.ADConcurrentHashMap;
import com.singularity.ee.util.javaspecific.collections.ADIterator;
import com.singularity.ee.util.logging.ILogger;
import com.singularity.ee.util.reflect.ReflectionUtilityCommon;
import com.singularity.ee.util.spi.IAgentAtomicBoolean;
import com.singularity.ee.util.system.SystemUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class StatMetricAggregatorFactory implements IMetricAggregatorFactory {

    public static AgentNodeProperties agentNodeProperties;
    public StatMetricAggregatorFactory(ILogger logger, IEventGenerationService egs, int maxMetricsAllowed, AgentNodeProperties agentNodeProperties) {
        this.logger = logger;
        this.egs = egs;
        this.maxMetricsAllowed = maxMetricsAllowed;
        this.agentNodeProperties = agentNodeProperties;
    }

    public final IMetricAggregator safeGetAggregator(AgentRawMetricIdentifier metricIdentifier) throws MetricReporterTypeMismatchException, MetricOverflowException {
        return this.safeGetAggregator(metricIdentifier, NO_OTHER_NAMES);
    }

    public IMetricAggregator safeGetAggregator(AgentRawMetricIdentifier metricIdentifier, AgentRawMetricIdentifier[] otherNames) throws MetricReporterTypeMismatchException, MetricOverflowException {
        IMetricAggregator aggregator = this.getMetricAggregator(metricIdentifier);
        if (aggregator == null) {
            if (this.isAtCapacity()) {
                return this.getEmptyAggregator();
            }

            aggregator = createNewAggregatorNONSTATIC(metricIdentifier.getMetricAggregatorType());
            logger.debug("Created aggregator: " + aggregator + " for metricIdentifier " + metricIdentifier.toString());

            if (metricExtendedLogger.isDebugEnabled()) {
                metricExtendedLogger.debug("Created aggregator: " + aggregator + " for metricIdentifier " + metricIdentifier.toString());
            }

            this.registerNewAggregator(aggregator, metricIdentifier, otherNames);
            logger.debug("Registered aggregator: " + aggregator + " for metricIdentifier " + metricIdentifier.toString());

            if (metricExtendedLogger.isDebugEnabled()) {
                metricExtendedLogger.debug("Registered aggregator: " + aggregator + " for metricIdentifier " + metricIdentifier.toString());
            }
        }

        aggregator = this.getMetricAggregator(metricIdentifier);
        if (!aggregator.getType().equals(metricIdentifier.getMetricAggregatorType())) {
            throw new MetricReporterTypeMismatchException("Aggregator of " + aggregator.getType() + " already exists for metric " + metricIdentifier);
        } else {
            return aggregator;
        }
    }

    public IMetricAggregator createAggregator(MetricAggregatorType type) {
        if (this.isAtCapacity()) {
            return this.getEmptyAggregator();
        } else {
            IMetricAggregator unregAggregator = createNewAggregatorNONSTATIC(type);
            this.addUnregisteredAggregator(unregAggregator);
            return unregAggregator;
        }
    }
    public IMetricAggregator createNewAggregatorNONSTATIC(MetricAggregatorType type) {
        switch (type) {
            case AVAILABILITY:
                return new AvailabilityMetricAggregator(); //unchanged, we want these to continue
            case AVERAGE:
                return new ManagedAverageMetricAggregator(agentNodeProperties);
            case ADVANCED_AVERAGE:
                return new ManagedAdvancedAverageMetricAggregator(agentNodeProperties);
            case SUM:
                return new ExtrapolatedSumMetricAggregator(agentNodeProperties);
            case OBSERVATION:
                return new ManagedObservationMetricAggregator(agentNodeProperties);
            case OBSERVATION_FOREVERINCREASING:
                return new ManagedObservationForeverIncreasingMetricAggregator(agentNodeProperties);
            case PERCENTILE:
            default:
                throw new IllegalArgumentException("Cannot create Aggregator of type" + type);
        }
    }

    protected static final int METRIC_REG_LIMIT_REACHED_INTERVAL_MS = 900000;
    protected static final AgentRawMetricIdentifier[] NO_OTHER_NAMES = new AgentRawMetricIdentifier[0];
    private static final String CAPACITY_REACHED_MESSAGE = "Metric registration limit of %d reached";
    private int maxMetricsAllowed;
    private final IAgentAtomicBoolean hasFiredOverloadAlert = new AgentAtomicBooleanImpl(false);
    protected final ILogger logger;
    protected static final IADLogger metricExtendedLogger = ADLoggerFactory.getLogger("com.singularity.METRICS.extendedLogger");
    protected final IEventGenerationService egs;


    public void setMaxMetricsAllowed(int maxMetricsAllowed) {
        this.logger.info("Current number of metrics is " + this.currentWeight());
        if (this.hasFiredOverloadAlert.compareAndSet(true, false)) {
            this.logger.info("Resetting metric limit alert logging threshold, agent will log if limit new limit is hit.");
        }

        this.maxMetricsAllowed = maxMetricsAllowed;
    }

    public int getMaxMetricsAllowed() {
        return this.maxMetricsAllowed;
    }


    public final IMetricAggregator registerAggregator(AgentRawMetricIdentifier metricIdentifier, IMetricAggregator metricAggregator) {
        return metricAggregator == DummyAggregator.getInstance() ? metricAggregator : this.moveMetricToRegistered(metricIdentifier, metricAggregator);
    }

    public final IMetricAggregator getAggregator(AgentRawMetricIdentifier metricIdentifier) throws MetricUnavailableException {
        IMetricAggregator registeredAggregator = this.getMetricAggregator(metricIdentifier);
        if (registeredAggregator == null) {
            throw new MetricUnavailableException("Aggregator does not exist for metric " + metricIdentifier);
        } else {
            return registeredAggregator;
        }
    }

    public final RawMetrics aggregateAll(int maxInactiveAllowed, ILogger logger) {
        List<AgentRawMetricData> registeredMetrics = new ArrayList();
        List<AgentRawMetricData> unregisteredMetrics = new ArrayList();
        this.collateAllAggregators(registeredMetrics, unregisteredMetrics, logger);
        this.notifyIfAtCapacity();
        Iterator var5 = this.getAllUnregisteredAggregators().iterator();

        while(var5.hasNext()) {
            IMetricAggregator aggregator = (IMetricAggregator)var5.next();
            aggregator.aggregate();
        }

        return new RawMetrics((AgentRawMetricData[]) CollectionHelper.collectionToArray(registeredMetrics, new AgentRawMetricData[registeredMetrics.size()]), (AgentRawMetricData[])CollectionHelper.collectionToArray(unregisteredMetrics, new AgentRawMetricData[unregisteredMetrics.size()]));
    }

    protected static void collateMetricWithIds(IMetricAggregator aggregator, Collection<? extends AgentRawMetricIdentifier> ids, List<AgentRawMetricData> registeredMetrics, List<AgentRawMetricData> unregisteredMetrics, ILogger logger) {
        boolean reportForRegistered = aggregator.isChanged();
        RawMetricValue rawMetricValue = aggregator.aggregate();
        if (rawMetricValue == null) {
            logger.warn("metric value null for: " + ids);
        } else {
            Iterator var7 = ids.iterator();

            while(var7.hasNext()) {
                AgentRawMetricIdentifier id = (AgentRawMetricIdentifier)var7.next();
                collateMetricWithId(id, aggregator, reportForRegistered, rawMetricValue, registeredMetrics, unregisteredMetrics, logger);
            }

        }
    }

    protected IMetricAggregator getEmptyAggregator() {
        if (this.hasFiredOverloadAlert.compareAndSet(false, true)) {
            this.logMetricLimitReached();
        }

        return DummyAggregator.getInstance();
    }

    private void logMetricLimitReached() {
        String msg = "Maximum metrics limit reached [" + this.maxMetricsAllowed + "] no new metrics can be created. This exception will not repeat until restart.";
        this.logger.error(msg, new MetricOverflowException(msg));
    }

    private void notifyIfAtCapacity() {
        if (this.isAtCapacity() && this.egs.getInternalEventGenerator().lastEventTimedOut(EventType.AGENT_METRIC_REG_LIMIT_REACHED, 900000)) {
            this.egs.getInternalEventGenerator().throwCappedInternalEvent(EventType.AGENT_METRIC_REG_LIMIT_REACHED, String.format("Metric registration limit of %d reached", this.maxMetricsAllowed));
            this.logMetricLimitReached();
        }

    }

    protected boolean isAtCapacity() {
        return this.currentWeight() >= this.maxMetricsAllowed;
    }

    private static void collateMetricWithId(AgentRawMetricIdentifier id, IMetricAggregator aggregator, boolean reportForRegistered, RawMetricValue rawMetricValue, List<AgentRawMetricData> registeredMetrics, List<AgentRawMetricData> unregisteredMetrics, ILogger logger) {
        if (id.getId() == 0L) {
            collateMetricIntoList(id, aggregator, rawMetricValue, unregisteredMetrics, logger, true);
        } else if (reportForRegistered) {
            collateMetricIntoList(id, aggregator, rawMetricValue, registeredMetrics, logger, false);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug(id.getId() + " " + id.getName() + " UNCHANGED " + aggregator.hashCode());
            }

            aggregator.resetLastAggregateValue();
        }

    }

    private static void collateMetricIntoList(AgentRawMetricIdentifier id, IMetricAggregator aggregator, RawMetricValue rawMetricValue, List<AgentRawMetricData> metricDataList, ILogger logger, boolean isUnregistered) {
        if (isNotValid(rawMetricValue)) {
            String metricType;
            if (isUnregistered) {
                metricType = "Unregistered Metric ";
            } else {
                metricType = "Registered Metric ";
            }

            logger.debug(metricType + id + ", Value " + rawMetricValue + " being dropped");
        } else {
            AgentRawMetricData data = new AgentRawMetricData();
            data.setMetricIdentifier(id);
            data.setMetricValue(rawMetricValue);
            metricDataList.add(data);
            if (logger.isDebugEnabled()) {
                String unregisteredWarning;
                if (isUnregistered) {
                    unregisteredWarning = " UNREGISTERED ";
                } else {
                    unregisteredWarning = " ";
                }

                logger.debug(id.getId() + " " + id.getName() + " " + rawMetricValue.getCurrent() + unregisteredWarning + aggregator.hashCode());
            }

        }
    }

    private static boolean isNotValid(RawMetricValue rawMetricValue) {
        return rawMetricValue.getCount() < 0L || rawMetricValue.getCurrent() < 0L || rawMetricValue.getMax() < 0L || rawMetricValue.getMin() < 0L || rawMetricValue.getSum() < 0L;
    }


    private static final String LOG_APPD_METRIC_AGGREGATORS_AT_LIMIT = "appdynamics.agent.log.metric.aggregators.at.limit";
    private final ADConcurrentHashMap<AgentRawMetricIdentifier, IMetricAggregator> aggregators = new ADConcurrentHashMap();
    private final ADConcurrentHashMap<AgentRawMetricIdentifier, IMetricAggregator> derivedAggregators = new ADConcurrentHashMap();
    private final ADConcurrentHashMap<IMetricAggregator, AgentRawMetricIdentifier[]> aggregatorsReverseMap = new ADConcurrentHashMap();
    private final ADConcurrentHashMap<IMetricAggregator, String> unregAggregators = new ADConcurrentHashMap();
    private static final boolean logMetricsAtLimit = SystemUtils.getProperty("appdynamics.agent.log.metric.aggregators.at.limit", "false").equalsIgnoreCase("true");
    private boolean metricNamesLoggedAtLimit = false;
    private long previousMetricLogTime = 0L;


    public AgentRawMetricIdentifier[] getRegisteredMetrics() {
        List<AgentRawMetricIdentifier> ids = new ArrayList();
        ADIterator<AgentRawMetricIdentifier[]> it = this.aggregatorsReverseMap.valuesIterator();

        while(it.hasNext()) {
            AgentRawMetricIdentifier[] identifiers = (AgentRawMetricIdentifier[])it.next();
            AgentRawMetricIdentifier[] var4 = identifiers;
            int var5 = identifiers.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                AgentRawMetricIdentifier identifier = var4[var6];
                ids.add(identifier);
            }
        }

        return (AgentRawMetricIdentifier[])CollectionHelper.collectionToArray(ids, new AgentRawMetricIdentifier[ids.size()]);
    }

    public void unregisterAggregator(AgentRawMetricIdentifier metricIdentifier) {
        if (metricIdentifier != null) {
            IMetricAggregator agg = (IMetricAggregator)this.aggregators.remove(metricIdentifier);
            if (agg != null) {
                this.aggregatorsReverseMap.remove(agg);
            }
        }

    }

    public IMetricAggregator registerDerivedAggregator(AgentRawMetricIdentifier metricIdentifier, IMetricAggregator metricAggregator) {
        if (this.derivedAggregators.get(metricIdentifier) == null) {
            this.derivedAggregators.putIfAbsent(metricIdentifier, metricAggregator);
        }

        return (IMetricAggregator)this.derivedAggregators.get(metricIdentifier);
    }

    public IMetricAggregator unregisterDerivedAggregator(AgentRawMetricIdentifier metricIdentifier) {
        return (IMetricAggregator)this.derivedAggregators.remove(metricIdentifier);
    }

    public void unregisterAggregator(IMetricAggregator aggregator) {
        if (aggregator != null) {
            AgentRawMetricIdentifier[] ids = (AgentRawMetricIdentifier[])this.aggregatorsReverseMap.get(aggregator);
            if (ids != null) {
                AgentRawMetricIdentifier[] var3 = ids;
                int var4 = ids.length;

                for(int var5 = 0; var5 < var4; ++var5) {
                    AgentRawMetricIdentifier id = var3[var5];
                    this.unregisterAggregator(id);
                }
            }
        }

    }

    public void clearAllAggregators() {
        this.logger.info("Clearing all the aggregators ");
        this.aggregators.clear();
        this.aggregatorsReverseMap.clear();
    }

    public void purge(AgentRawMetricIdentifier unregisteredMetricIdentifier) {
        IMetricAggregator aggregator = this.getMetricAggregator(unregisteredMetricIdentifier);
        this.purge(aggregator);
    }

    public void purge(IMetricAggregator aggregator) {
        if (aggregator != null) {
            this.unregisterAggregator(aggregator);
            this.unregAggregators.remove(aggregator);
        }

    }

    protected IMetricAggregator getMetricAggregator(AgentRawMetricIdentifier metricId) {
        return (IMetricAggregator)this.aggregators.get(metricId);
    }

    protected void registerNewAggregator(IMetricAggregator aggregator, AgentRawMetricIdentifier metricIdentifier, AgentRawMetricIdentifier[] otherNames) {
        if (this.aggregators.putIfAbsent(metricIdentifier, aggregator) == null) {
            if (otherNames.length == 0) {
                this.aggregatorsReverseMap.put(aggregator, new AgentRawMetricIdentifier[]{metricIdentifier});
            } else {
                AgentRawMetricIdentifier[] list = new AgentRawMetricIdentifier[otherNames.length + 1];
                list[0] = metricIdentifier;
                System.arraycopy(otherNames, 0, list, 1, otherNames.length);
                this.aggregatorsReverseMap.put(aggregator, list);
                AgentRawMetricIdentifier[] var5 = otherNames;
                int var6 = otherNames.length;

                for(int var7 = 0; var7 < var6; ++var7) {
                    AgentRawMetricIdentifier otherName = var5[var7];
                    this.aggregators.putIfAbsent(otherName, aggregator);
                }
            }
        } else {
            this.logger.info("Multiple aggregator attempt to track the same Identifier: [" + metricIdentifier + "]. The old one will be used");
        }

    }

    protected void addUnregisteredAggregator(IMetricAggregator unregAggregator) {
        this.unregAggregators.put(unregAggregator, "");
    }

    protected IMetricAggregator moveMetricToRegistered(AgentRawMetricIdentifier metricIdentifier, IMetricAggregator metricAggregator) {
        if (this.getMetricAggregator(metricIdentifier) == null) {
            this.aggregators.putIfAbsent(metricIdentifier, metricAggregator);
            this.aggregatorsReverseMap.putIfAbsent(metricAggregator, new AgentRawMetricIdentifier[]{metricIdentifier});
        }

        if (this.unregAggregators.remove(metricAggregator) == null && this.logger.isDebugEnabled()) {
            this.logger.debug("FAILED to remove from unreg" + metricIdentifier + metricAggregator);
        }

        return this.getMetricAggregator(metricIdentifier);
    }

    protected void collateAllAggregators(List<AgentRawMetricData> registeredMetrics, List<AgentRawMetricData> unregisteredMetrics, ILogger logger) {
        ADIterator<IMetricAggregator> aggregatorKeys = this.aggregatorsReverseMap.keySetIterator();

        while(aggregatorKeys.hasNext()) {
            IMetricAggregator aggregator = (IMetricAggregator)aggregatorKeys.next();
            collateMetricWithIds(aggregator, CollectionHelper.asList(this.aggregatorsReverseMap.get(aggregator)), registeredMetrics, unregisteredMetrics, logger);
        }

        if (this.derivedAggregators.size() > 0) {
            Collection<AgentRawMetricIdentifier> derivedAggregatorKeys = this.derivedAggregators.getAllKeys();
            Iterator var6 = derivedAggregatorKeys.iterator();

            while(var6.hasNext()) {
                AgentRawMetricIdentifier derivedAggregatorKey = (AgentRawMetricIdentifier)var6.next();
                collateMetricWithIds((IMetricAggregator)this.derivedAggregators.get(derivedAggregatorKey), CollectionHelper.asList(new AgentRawMetricIdentifier[]{derivedAggregatorKey}), registeredMetrics, unregisteredMetrics, logger);
            }
        }

    }

    protected Collection<IMetricAggregator> getAllUnregisteredAggregators() {
        return this.unregAggregators.getAllKeys();
    }

    protected int currentWeight() {
        int currentTotalWeight = this.aggregators.fastApproximateSize() + this.unregAggregators.fastApproximateSize();
        if (currentTotalWeight >= this.getMaxMetricsAllowed() && metricExtendedLogger.isDebugEnabled()) {
            long timeNow = System.currentTimeMillis();
            if (timeNow - this.previousMetricLogTime >= 60000L) {
                metricExtendedLogger.debug("The metric aggregators are at capacity [" + this.getMaxMetricsAllowed() + "].Registered [" + this.aggregators.fastApproximateSize() + "] and Unregistered [" + this.unregAggregators.fastApproximateSize() + "]");
                this.previousMetricLogTime = timeNow;
            }

            if (logMetricsAtLimit && !this.metricNamesLoggedAtLimit) {
                this.logMetricNamesOrAggregators();
                this.metricNamesLoggedAtLimit = true;
            }
        }

        return currentTotalWeight;
    }

    private void logMetricNamesOrAggregators() {
        StringBuilder regAggSb = new StringBuilder();
        regAggSb.append("Dumping Registered Metrics: \n");
        Iterator var2 = this.aggregators.getAllKeys().iterator();

        while(var2.hasNext()) {
            AgentRawMetricIdentifier metricIdentifier = (AgentRawMetricIdentifier)var2.next();
            regAggSb.append(metricIdentifier).append("\n");
        }

        regAggSb.append("End of Registered Metrics Dump.\n");
        StringBuilder unRegAggSb = new StringBuilder();
        unRegAggSb.append("Dumping Unregistered Metrics: \n");
        Iterator var6 = this.unregAggregators.getAllKeys().iterator();

        while(var6.hasNext()) {
            IMetricAggregator metricAggregator = (IMetricAggregator)var6.next();
            unRegAggSb.append(metricAggregator).append("\n");
        }

        unRegAggSb.append("End of Unregistered Metrics Dump.\n");
        metricExtendedLogger.debug(regAggSb.toString());
        metricExtendedLogger.debug(unRegAggSb.toString());
    }

    private void forceMetricReporting() {
        Iterator var1 = this.aggregators.getAllValues().iterator();

        while(var1.hasNext()) {
            IMetricAggregator aggregator = (IMetricAggregator)var1.next();
            if (aggregator instanceof AMetricAggregator) {
                ((AMetricAggregator)aggregator).resetReportedIntervals();
            }
        }

    }

    public void notifyChannelListener(ConnectivityEvent connectivityEvent) {
        if (connectivityEvent == ConnectivityEvent.RECONNECTED) {
            this.forceMetricReporting();
        }

    }
}
