package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EndpointConfig {

    public String app;
    public String hostname;
    public String name;
    public Integer port;
    public Integer tlsPort;
}
