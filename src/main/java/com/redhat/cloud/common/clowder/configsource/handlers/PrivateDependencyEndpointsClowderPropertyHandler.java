package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class PrivateDependencyEndpointsClowderPropertyHandler extends DependencyEndpointsClowderPropertyHandler {

    private static final String CLOWDER_PRIVATE_DEPENDENCY_ENDPOINTS = "clowder.private-dependency-endpoints.";

    public PrivateDependencyEndpointsClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public String handle(String property, ClowderConfigSource source) {
        return processDependencyEndpoints(property, source, clowderConfig.privateDependencyEndpoints);
    }

    @Override
    protected String getPropertyEndpointKey() {
        return CLOWDER_PRIVATE_DEPENDENCY_ENDPOINTS;
    }
}
