package com.singularity.ee.service.statisticalSampler;

import java.util.HashMap;
import java.util.Map;

public class MetaData {
    public static final String VERSION = "v${version}";
    public static final String BUILDTIMESTAMP = "${build.time}";
    public static final String GECOS = "John Southerland josouthe@cisco.com";
    public static final String GITHUB = "https://github.com/jbsouthe/AppDynamics-Statistical-Dynamic-Service";
    public static final String DEVNET = "";
    public static final String SUPPORT = "https://github.com/jbsouthe/AppDynamics-Statistical-Dynamic-Service/issues";


    public static Map<String,String> getAsMap() {
        Map<String,String> map = new HashMap<>();
        map.put("statisticalSampler-version", VERSION);
        map.put("statisticalSampler-buildTimestamp", BUILDTIMESTAMP);
        map.put("statisticalSampler-developer", GECOS);
        map.put("statisticalSampler-github", GITHUB);
        map.put("statisticalSampler-devnet", DEVNET);
        map.put("statisticalSampler-support", SUPPORT);
        return map;
    }
}
