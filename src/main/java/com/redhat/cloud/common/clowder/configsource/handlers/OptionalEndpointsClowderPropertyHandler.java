package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class OptionalEndpointsClowderPropertyHandler extends EndpointsClowderPropertyHandler {

    private static final String CLOWDER_OPTIONAL_ENDPOINTS = "clowder.optional-endpoints.";

    public OptionalEndpointsClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public String handle(String property, ClowderConfigSource configSource) {
        try {
            if (clowderConfig.endpoints == null) {
                configSource.getLogger().infof("No endpoints section found. Returning empty string for the \"%s\" configuration key", property);
                return "";
            }

            return processEndpoints(property, configSource, clowderConfig.endpoints, "Endpoint");
        } catch (final IllegalStateException e) {
            configSource.getLogger().errorf("Failed to load config key '%s' from the Clowder configuration: %s", property, e.getMessage());
            throw e;
        }
    }

    @Override
    protected String getPropertyEndpointKey() {
        return CLOWDER_OPTIONAL_ENDPOINTS;
    }
}
