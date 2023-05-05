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
        map.put("agentupdater-version", VERSION);
        map.put("agentupdater-buildTimestamp", BUILDTIMESTAMP);
        map.put("agentupdater-developer", GECOS);
        map.put("agentupdater-github", GITHUB);
        map.put("agentupdater-devnet", DEVNET);
        map.put("agentupdater-support", SUPPORT);
        return map;
    }
}
