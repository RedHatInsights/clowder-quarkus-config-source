package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class OptionalPrivateDependencyEndpointsClowderPropertyHandler
        extends DependencyEndpointsClowderPropertyHandler {

    private static final String CLOWDER_OPTIONAL_PRIVATE_DEPENDENCY_ENDPOINTS =
            "clowder.optional-private-dependency-endpoints.";

    public OptionalPrivateDependencyEndpointsClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public String handle(String property, ClowderConfigSource source) {
        return processDependencyEndpoints(property, source, clowderConfig.privateDependencyEndpoints);
    }

    @Override
    protected String getPropertyEndpointKey() {
        return CLOWDER_OPTIONAL_PRIVATE_DEPENDENCY_ENDPOINTS;
    }
}
