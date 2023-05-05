# AppDynamics Java Agent Statistical Sampling Dynamic Service Extension

[![published](https://static.production.devnetcloud.com/codeexchange/assets/images/devnet-published.svg)](https://developer.cisco.com/codeexchange/github/repo/jbsouthe/AppDynamicsAgentUpdater) 
[Github Link To This Repo](https://github.com/jbsouthe/AppDynamicsAgentUpdater)

This extension allows the AppDynamics Java Agent to decide on startup whether or not it should send metrics to the controller. In very large tiers, this data is not needed because a sample can be used to estimate actual metrics and support higher agent loads.

This plugin is in BETA and not supported by AppDynamics, please report any issues to this github repository and our team will respond as soon as possible.
Thanks, John

## Theory of Operation "how does it work"

The agent dynamic service needs to be installed and then node properties from the Controller UI will dictate how it acts
setting the following will cause it to perform the updates. The service will run and check for upgrades every 3 minutes, so when changing these parameters make sure to allow time to see execution.

    "agent.statisticalSampler.enabled" - boolean, setting this to true causes this service to come alive
    "agent.statisticalSampler.percentage" - the percentage of agents sending data, recommended 10%, as an int 1-90, if higher than 90 we will select 90, if lower than 1 we set to 1
    
## Installation - You only have to do this once

Some setup. This should be installed in the < agent install dir >/ver22.###/external-services/agent-updater directory
the < agent intall dir >/ver22.###/conf/app-agent-config.xml at line 120 has to have signing disabled in the "Dynamic Services" section:

    <configuration-properties>
        <property name="external-service-directory" value="external-services"/>
        <property name="enable-jar-signing" value="false"/>
    </configuration-properties>

![Agent Config File Example](doc-images/agent-config-edit.png)


## How to "do it"

Custom node properties control these activities. Setting the <B>"agent.upgrader.version.preferred"</B> node property will attempt to keep the agent at the version specified to the most significant part of the version entered. 
If you want to make sure you have the latest version for 2023, just enter "23", but if you want to make sure you have all the hot fixes or builds for April 2023, enter "23.4". Do not enter "24" if no releases exist for 2024, or the agent will not be upgraded until a release is available.

![Node Property Example](doc-images/AgentUpdaterNodeProperties.png)
