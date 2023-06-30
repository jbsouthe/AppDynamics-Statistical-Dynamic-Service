package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.appagent.kernel.LifeCycleManager;
import com.singularity.ee.agent.appagent.kernel.ServiceComponent;
import com.singularity.ee.agent.appagent.kernel.spi.IDynamicService;
import com.singularity.ee.agent.appagent.kernel.spi.IDynamicServiceManager;
import com.singularity.ee.agent.appagent.kernel.spi.IServiceContext;
import com.singularity.ee.agent.appagent.kernel.spi.data.IServiceConfig;
import com.singularity.ee.agent.appagent.kernel.spi.exception.ConfigException;
import com.singularity.ee.agent.appagent.kernel.spi.exception.ServiceStartException;
import com.singularity.ee.agent.appagent.kernel.spi.exception.ServiceStopException;
import com.singularity.ee.agent.appagent.services.bciengine.JavaAgentManifest;
import com.singularity.ee.agent.commonservices.metricgeneration.aggregation.SumMetricAggregator;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.AgentRawMetricIdentifier;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.IMetricReporterFactory;
import com.singularity.ee.agent.commonservices.metricgeneration.metrics.spi.MetricAggregatorType;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.util.javaspecific.threads.IAgentRunnable;
import com.singularity.ee.util.spi.AgentTimeUnit;
import com.singularity.ee.util.spi.IAgentScheduledExecutorService;
import com.singularity.ee.util.spi.IAgentScheduledFuture;

public class StatisticalSamplerService implements IDynamicService {

    private AgentNodeProperties agentNodeProperties = new AgentNodeProperties();
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.StatisticalSamplerService");
    private boolean isServiceStarted = false;
    private IAgentScheduledFuture scheduledTaskFuture, scheduledMetricTaskFuture;
    private StatisticalDisableSendingDataTask statisticalDisableSendingDataTask;
    private final ServiceComponent serviceComponent = LifeCycleManager.getInjector();
    private static final String MINIMUM_JAVA_AGENT_VERSION_REQUIRED = "23.7.0.34866";
    private long taskInitialDelay=0;
    private long taskInterval=15; //every 1 minute, with the task itself only randomly deciding every 15 minutes
    private IAgentScheduledExecutorService scheduler;
    private IServiceContext iServiceContext;
    private IDynamicServiceManager dynamicServiceManager;
    private JavaAgentVersion javaAgentVersion;

    public StatisticalSamplerService() {
        logger.info(String.format("Initializing Agent %s %s build date %s by %s visit %s for the most up to date information.",
                MetaData.SERVICENAME, MetaData.VERSION, MetaData.BUILDTIMESTAMP, MetaData.GECOS, MetaData.DEVNET));
    }

    public StatisticalSamplerService(AgentNodeProperties agentNodeProperties, long taskInitialDelay, long taskInterval) {
        this();
        this.agentNodeProperties = agentNodeProperties;
        this.taskInitialDelay = taskInitialDelay;
        this.taskInterval = taskInterval;
    }

    @Override
    public String getName() {
        return MetaData.SERVICENAME;
    }

    @Override
    public void setServiceContext(IServiceContext iServiceContext) {
        logger.info(String.format("Setting Service Context to %s",iServiceContext));
        this.iServiceContext=iServiceContext;
        this.scheduler = iServiceContext.getAgentScheduler();
        this.javaAgentVersion = new JavaAgentVersion(JavaAgentManifest.parseManifest(iServiceContext.getInstallDir()).getJavaAgentVersion());
    }

    @Override
    public void configure(IServiceConfig iServiceConfig) throws ConfigException {

    }

    @Override
    public void start() throws ServiceStartException {
        new AgentNodePropertyListener(this);
        if( this.isServiceStarted ) {
            logger.info("Agent " + this.getName() + " is already started");
            return;
        }
        if (this.scheduler == null) {
            throw new ServiceStartException("Scheduler is not set, so unable to start the "+ MetaData.SERVICENAME);
        }
        if (this.serviceComponent == null) {
            throw new ServiceStartException("Dagger not initialised, so cannot start the "+ MetaData.SERVICENAME);
        }
        if( this.javaAgentVersion == null ) {
            throw new ServiceStartException("Java Agent Version not yet set, Service Context must not be set, so cannot start the "+ MetaData.SERVICENAME);
        }
        if( this.javaAgentVersion.compareTo(new JavaAgentVersion(MINIMUM_JAVA_AGENT_VERSION_REQUIRED)) == -1 ) {
            throw new ServiceStartException(String.format("Java Agent Version '%s' is less than the minimum required version '23.6.0.0', so cannot start the %s",this.javaAgentVersion, MetaData.SERVICENAME));
        }
        this.scheduledTaskFuture = this.scheduler.scheduleAtFixedRate(this.createTask(this.serviceComponent), 0, this.taskInterval, AgentTimeUnit.SECONDS);
        this.scheduledMetricTaskFuture = this.scheduler.scheduleAtFixedRate(this.createMetricTask(this.serviceComponent), 0, 60, AgentTimeUnit.SECONDS);
        this.isServiceStarted = true;
        logger.info("Started " + this.getName() + " with initial delay " + this.taskInitialDelay + ", and with interval " + this.taskInterval + " in Seconds");

    }

    private IAgentRunnable createMetricTask(ServiceComponent serviceComponent) {
        logger.info("Creating Metric Sending Task for "+ MetaData.SERVICENAME);
        return new StatisticalSamplerMetricTask( this, this.agentNodeProperties, serviceComponent, iServiceContext);
    }

    private IAgentRunnable createTask(ServiceComponent serviceComponent) {
        logger.info("Creating Task for "+ MetaData.SERVICENAME);
        this.statisticalDisableSendingDataTask = new StatisticalDisableSendingDataTask( this, this.agentNodeProperties, serviceComponent, iServiceContext);
        return statisticalDisableSendingDataTask;
    }

    @Override
    public void allServicesStarted() {

    }

    @Override
    public void stop() throws ServiceStopException {
        if (!this.isServiceStarted) {
            logger.info("Service " + this.getName() + " not running");
            return;
        }
        if (this.scheduledTaskFuture != null && !this.scheduledTaskFuture.isCancelled() && !this.scheduledTaskFuture.isDone()) {
            if( this.statisticalDisableSendingDataTask != null ) this.statisticalDisableSendingDataTask.enableEverything();
            this.scheduledTaskFuture.cancel(true);
            this.scheduledTaskFuture = null;
            this.scheduledMetricTaskFuture.cancel(true);
            this.scheduledMetricTaskFuture = null;
            this.isServiceStarted = false;
        }
        IMetricReporterFactory iMetricReporterFactory = serviceComponent.getMetricHandler().getAggregatorFactory();
        for ( AgentRawMetricIdentifier agentRawMetricIdentifier : iMetricReporterFactory.getRegisteredMetrics() ) {
            if( agentRawMetricIdentifier.getMetricAggregatorType().equals(MetricAggregatorType.SUM))
                iMetricReporterFactory.registerAggregator(agentRawMetricIdentifier, new SumMetricAggregator());
        }
    }

    @Override
    public void hotDisable() {
        logger.info("Disabling "+ MetaData.SERVICENAME);
        try {
            this.stop();
        }
        catch (ServiceStopException e) {
            logger.error("unable to stop the services", (Throwable)e);
        }
    }

    @Override
    public void hotEnable() {
        logger.info("Enabling "+ MetaData.SERVICENAME);
        try {
            this.start();
        }
        catch (ServiceStartException e) {
            logger.error("unable to start the services", (Throwable)e);
        }

    }

    @Override
    public void setDynamicServiceManager(IDynamicServiceManager iDynamicServiceManager) {
        this.dynamicServiceManager = iDynamicServiceManager;
    }

    public IServiceContext getServiceContext() {
        return iServiceContext;
    }

    public AgentNodeProperties getAgentNodeProperties() {
        return agentNodeProperties;
    }
}
