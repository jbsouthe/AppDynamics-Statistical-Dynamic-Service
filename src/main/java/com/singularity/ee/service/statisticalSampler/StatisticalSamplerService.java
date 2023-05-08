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
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.StatisticalSamplerService");
    private boolean isServiceStarted = false;
    private IAgentScheduledFuture scheduledTaskFuture, scheduledMetricTaskFuture;
    private final ServiceComponent serviceComponent = LifeCycleManager.getInjector();
    private long taskInitialDelay=0;
    private long taskInterval=900; //every 15 minutes
    private IAgentScheduledExecutorService scheduler;
    private IServiceContext iServiceContext;
    private IDynamicServiceManager dynamicServiceManager;

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
