package com.singularity.ee.service.statisticalSampler;

import com.singularity.ee.agent.appagent.kernel.spi.data.IServiceConfig;
import com.singularity.ee.agent.util.log4j.ADLoggerFactory;
import com.singularity.ee.agent.util.log4j.IADLogger;
import com.singularity.ee.util.string.StringOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;

public class AgentNodeProperties extends Observable {
    private static final IADLogger logger = ADLoggerFactory.getLogger((String)"com.singularity.dynamicservice.statisticalSampler.AgentNodeProperties");
    public static final String[] NODE_PROPERTIES = new String[]{"agent.statisticalSampler.enabled", "agent.statisticalSampler.percentage", "agent.statisticalSampler.maxEvents", "agent.statisticalSampler.decisionDurationMinutes", "agent.statisticalSampler.holdMaxEvents", "agent.statisticalSampler.isMetricThrottled", "agent.statisticalSampler.isEventThrottled"};
    private final Map<String, String> properties = new HashMap<>();

    public void initializeConfigs(IServiceConfig serviceConfig) {
        Map configProperties = serviceConfig.getConfigProperties();
        if( configProperties != null ) {
            boolean enabled = StringOperations.safeParseBoolean((String)((String)configProperties.get("agent.statisticalSampler.enabled")), (boolean)false);
            this.properties.put("agent.statisticalSampler.enabled", Boolean.toString(enabled));
            this.properties.put("agent.statisticalSampler.percentage", (String)configProperties.get("agent.statisticalSampler.percentage"));
            this.properties.put("agent.statisticalSampler.maxEvents", (String)configProperties.get("agent.statisticalSampler.maxEvents"));
            this.properties.put("agent.statisticalSampler.decisionDurationMinutes", (String)configProperties.get("agent.statisticalSampler.decisionDurationMinutes"));
            logger.info("Initializing the properties " + this);
        } else {
            logger.error("Config properties map is null?!?!");
        }
    }

    public String getProperty( String name ) {
        return this.properties.get(name);
    }

    public void updateProperty( String name, String value ) {
        String existingPropertyValue = this.properties.get(name);
        if( !StringOperations.isEmpty((String)value) && !value.equals(existingPropertyValue)) {
            this.properties.put(name, value);
            logger.info("updated property = " + name + " with value = " + value);
            this.notifyMonitoringService(name);
        } else {
            logger.info("did not update property = " + name + " because it was either unchanged or empty");
        }
    }

    protected void notifyMonitoringService(String name) {
        this.setChanged();
        this.notifyObservers(name);
    }

    public String toString() {
        return "AgentNodeProperties{properties=" + this.properties + '}';
    }

    public boolean isEnabled() {
        return StringOperations.safeParseBoolean((String)this.getProperty("agent.statisticalSampler.enabled"), (boolean)false);
    }

    public boolean isMaxEventsSet() { return this.properties.get("agent.statisticalSampler.maxEvents") != null; }

    public Integer getMaxEvents() {
        int value = StringOperations.safeParseInteger((String)this.getProperty("agent.statisticalSampler.maxEvents"), 50);
        if( value < 0 ) return 0;
        if( value > 100 ) return 100;
        return value;
    }

    public void setHoldMaxEvents( int value ) { this.properties.put("agent.statisticalSampler.holdMaxEvents", String.valueOf(value)); }
    public Integer getHoldMaxEvents() { return StringOperations.safeParseInteger(getProperty("agent.statisticalSampler.holdMaxEvents")); }

    public Integer getEnabledPercentage() {
        int value = StringOperations.safeParseInteger(this.properties.get("agent.statisticalSampler.percentage"), 10 );
        if( value < 1 ) return 1;
        if( value > 100 ) return 100;
        return value;
    }

    public void setMetricThrottled( boolean b ) { this.properties.put("agent.statisticalSampler.isMetricThrottled", String.valueOf(b)); }
    public boolean isMetricThrottled() { return StringOperations.safeParseBoolean( this.properties.get("agent.statisticalSampler.isMetricThrottled"), false); }
    public void setEventThrottled( boolean b ) { this.properties.put("agent.statisticalSampler.isEventThrottled", String.valueOf(b)); }
    public boolean isEventThrottled() { return StringOperations.safeParseBoolean( this.properties.get("agent.statisticalSampler.isEventThrottled"), false); }

    public long getDecisionDuration() { return StringOperations.safeParseLong( this.properties.get("agent.statisticalSampler.decisionDurationMinutes"), 15); }
}
