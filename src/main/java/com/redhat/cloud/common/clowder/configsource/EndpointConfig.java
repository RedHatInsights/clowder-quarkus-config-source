package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class EndpointConfig {

    public String app;
    public String hostname;
    public String name;
    public Integer port;
    public Integer tlsPort;
}
