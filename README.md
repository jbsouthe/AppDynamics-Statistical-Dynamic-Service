# AppDynamics Java Agent Statistical Sampling Dynamic Service Extension
 
[Github Link To This Repo](https://github.com/jbsouthe/AppDynamics-Statistical-Dynamic-Service)

This extension allows the AppDynamics Java Agent to decide on startup whether or not it should send metrics to the controller. In very large tiers, this data is not needed because a sample can be used to estimate actual metrics and support higher agent loads.

This plugin is in BETA and not supported by AppDynamics, please report any issues to this github repository and our team will respond as soon as possible.
Thanks, John

## Theory of Operation "how does it work"

The agent dynamic service needs to be installed and then node properties from the Controller UI will dictate how it acts
setting the following will cause it to perform the updates. The service will run and check for upgrades every 3 minutes, so when changing these parameters make sure to allow time to see execution.

    "agent.statisticalSampler.enabled" - boolean, setting this to true causes this service to come alive
    "agent.statisticalSampler.percentage" - the percentage of agents sending data, recommended 10%, as an int 1-90, if higher than 90 we will select 90, if lower than 1 we set to 1

Once enabled, it will determine randomly if the agent should enable metrics, and then it will run every hour and decide once again whether it will continue to disable/enable metrics and randomly make the decision again.
The logic of this is:

    Integer percentageOfNodesSendingData = agentNodeProperties.getEnabledPercentage(); //assume this is 10% for the examples in the logic below
    int r = (int) (Math.random() *100);
    if( r > percentageOfNodesSendingData ) { //if r > 10% (the large number
        logger.info("This Agent WILL NOT be sending data, it is randomly selected to disable metrics to the controller r="+r);
        serviceComponent.getMetricHandler().getMetricService().hotDisable(); //disable all metrics
        return;
    } //else r <= 10%; so continue
        logger.info("This Agent WILL be sending data, it is randomly selected to enable metrics to the controller r="+r);
        serviceComponent.getMetricHandler().getMetricService().hotEnable(); //enable all metrics again :)

## Installation - You only have to do this once

Some setup. This should be installed in the < agent install dir >/ver22.###/external-services/Statistical-Sampling directory
the < agent intall dir >/ver22.###/conf/app-agent-config.xml at line 120 has to have signing disabled in the "Dynamic Services" section:

    <configuration-properties>
        <property name="external-service-directory" value="external-services"/>
        <property name="enable-jar-signing" value="false"/>
    </configuration-properties>

![Agent Config File Example](doc-images/agent-config-edit.png)


## How to "do it"

Custom node properties control these activities. Setting the <B>"agent.statisticalSampler.enabled"</B> node property to true will enable this service, when this service is disabled it will enable metrics collection as it removes itself. 
Setting the node property <B>"agent.statisticalSampler.percentage"</B> to an integer between 1 and 99 will set the percentage of nodes enabled to send metrics as part of this statistical sample.

![Node Property Example](doc-images/AgentUpdaterNodeProperties.png)
