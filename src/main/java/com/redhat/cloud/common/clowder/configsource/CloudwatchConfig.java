package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class CloudwatchConfig {

    public String accessKeyId;
    public String logGroup;
    public String region;
    public String secretAccessKey;
}
