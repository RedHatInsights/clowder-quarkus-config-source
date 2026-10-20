package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class TopicConfig {

    public String requestedName;
    public String name;
}
