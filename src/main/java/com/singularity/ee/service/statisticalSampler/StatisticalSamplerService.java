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
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.util.javaspecific.threads.IAgentRunnable;
import com.singularity.ee.util.spi.AgentTimeUnit;
import com.singularity.ee.util.spi.IAgentScheduledExecutorService;
import com.singularity.ee.util.spi.IAgentScheduledFuture;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class StatisticalSamplerService implements IDynamicService {

    private AgentNodeProperties agentNodeProperties = new AgentNodeProperties();
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.ee.service.statisticalSampler.StatisticalSamplerService");
    private boolean isServiceStarted = false;
    private IAgentScheduledFuture scheduledTaskFuture, scheduledMetricTaskFuture;
    private final ServiceComponent serviceComponent = LifeCycleManager.getInjector();
    private long taskInitialDelay=0;
    private long taskInterval=3600; //every hour = 60*60=3600
    private IAgentScheduledExecutorService scheduler;
    private IServiceContext iServiceContext;
    private IDynamicServiceManager dynamicServiceManager;

    public StatisticalSamplerService() {
        logger.info(String.format("Initializing Agent Statistical Sampler Service %s build date %s by %s visit %s for the most up to date information.",
                MetaData.VERSION, MetaData.BUILDTIMESTAMP, MetaData.GECOS, MetaData.DEVNET));
    }

    public StatisticalSamplerService(AgentNodeProperties agentNodeProperties, long taskInitialDelay, long taskInterval) {
        this();
        this.agentNodeProperties = agentNodeProperties;
        this.taskInitialDelay = taskInitialDelay;
        this.taskInterval = taskInterval;
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public void setServiceContext(IServiceContext iServiceContext) {
        logger.info(String.format("Setting Service Context to %s",iServiceContext));
        this.iServiceContext=iServiceContext;
        this.scheduler = iServiceContext.getAgentScheduler();
    }

    @Override
    public void configure(IServiceConfig iServiceConfig) throws ConfigException {

    }

    @Override
    public void start() throws ServiceStartException {
        new AgentNodePropertyListener(this);
        if(!agentNodeProperties.isEnabled()) {
            logger.info("Service " + this.getName() + " is not enabled.  So not starting this service.  To start it enable the node property agent.statisticalSampler.enabled");
            return;
        }
        if( this.isServiceStarted ) {
            logger.info("Service " + this.getName() + " is already started");
            return;
        }
        if (this.scheduler == null) {
            throw new ServiceStartException("Scheduler is not set, so unable to start the agent statistical sampler service");
        }
        if (this.serviceComponent == null) {
            throw new ServiceStartException("Dagger not initialised, so cannot start the agent statistical sampler service");
        }
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusHours(1).truncatedTo(ChronoUnit.HOURS);
        Duration duration = Duration.between(start, end);
        this.scheduledTaskFuture = this.scheduler.scheduleAtFixedRate(this.createTask(this.serviceComponent), duration.getSeconds(), this.taskInterval, AgentTimeUnit.SECONDS);
        this.scheduledMetricTaskFuture = this.scheduler.scheduleAtFixedRate(this.createMetricTask(this.serviceComponent), 0, 1, AgentTimeUnit.MINUTES);
        this.isServiceStarted = true;
        logger.info("Started " + this.getName() + " with initial delay " + this.taskInitialDelay + ", and with interval " + this.taskInterval + " in Seconds");

    }

    private IAgentRunnable createMetricTask(ServiceComponent serviceComponent) {
        logger.info("Creating Metric Sending Task for agent statistical sampler");
        return new StatisticalSamplerMetricTask( this, this.agentNodeProperties, serviceComponent, iServiceContext);
    }

    private IAgentRunnable createTask(ServiceComponent serviceComponent) {
        logger.info("Creating Task for agent statistical sampler");
        return new StatisticalDisableSendingDataTask( this, this.agentNodeProperties, serviceComponent, iServiceContext);
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
            this.scheduledTaskFuture.cancel(true);
            this.scheduledTaskFuture = null;
            this.scheduledMetricTaskFuture.cancel(true);
            this.scheduledMetricTaskFuture = null;
            this.isServiceStarted = false;
            serviceComponent.getMetricHandler().getMetricService().hotEnable(); // when we stop the service, enable metrics again
            serviceComponent.getEventHandler().getEventService().hotEnable(); //enable all events again :)
            ReflectionHelper.setMaxEvents( serviceComponent.getEventHandler().getEventService(), agentNodeProperties.getHoldMaxEvents() );
        }
    }

    @Override
    public void hotDisable() {
        logger.info("Disabling agent statistical sampler service");
        try {
            this.stop();
        }
        catch (ServiceStopException e) {
            logger.error("unable to stop the services", (Throwable)e);
        }
    }

    @Override
    public void hotEnable() {
        logger.info("Enabling agent updater service");
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
