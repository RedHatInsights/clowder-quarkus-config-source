package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureFlagsConfig {

    public String hostname;
    public int port;
    public String clientAccessToken;
    public String scheme;
}
