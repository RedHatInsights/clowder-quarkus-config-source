package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class FeatureFlagsConfig {

    public String hostname;
    public Integer port;
    public String clientAccessToken;
    public String scheme;
}
