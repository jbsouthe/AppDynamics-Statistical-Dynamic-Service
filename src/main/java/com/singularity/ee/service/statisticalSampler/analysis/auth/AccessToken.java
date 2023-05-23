package com.singularity.ee.service.statisticalSampler.analysis.auth;

import java.util.Date;

public class AccessToken {
    public String access_token = null;
    public int expires_in = 0;
    public transient long expires_at = 0;
    public boolean isExpired() {
        return expires_at < System.currentTimeMillis();
    }
}
