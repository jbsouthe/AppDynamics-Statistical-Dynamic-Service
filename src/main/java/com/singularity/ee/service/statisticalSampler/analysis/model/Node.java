package com.singularity.ee.service.statisticalSampler.analysis.model;

public class Node {
    public String name, type, tierName, machineName, machineOSType, machineAgentVersion, appAgentVersion, agentType;
    public int id, tierId, machineId;
    public boolean machineAgentPresent, appAgentPresent;
}
