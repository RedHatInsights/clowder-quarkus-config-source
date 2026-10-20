package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class LoggingConfig {

    public CloudwatchConfig cloudwatch;
    public String type;
}
