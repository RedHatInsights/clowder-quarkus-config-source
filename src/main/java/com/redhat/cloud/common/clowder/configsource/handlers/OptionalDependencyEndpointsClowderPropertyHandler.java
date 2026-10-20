package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class OptionalDependencyEndpointsClowderPropertyHandler extends DependencyEndpointsClowderPropertyHandler {

    private static final String CLOWDER_OPTIONAL_DEPENDENCY_ENDPOINTS = "clowder.optional-dependency-endpoints.";

    public OptionalDependencyEndpointsClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public String handle(String property, ClowderConfigSource source) {
        return processDependencyEndpoints(property, source, clowderConfig.dependencyEndpoints);
    }

    @Override
    protected String getPropertyEndpointKey() {
        return CLOWDER_OPTIONAL_DEPENDENCY_ENDPOINTS;
    }
}
