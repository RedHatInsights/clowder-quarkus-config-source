package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.Map;

/** Versioned dependency metadata, keyed by application and then deployment. */
@RegisterForReflection
public class DependencyEndpointsConfig {

    public Map<String, Map<String, DependencyEndpointConfig>> v2;
}
