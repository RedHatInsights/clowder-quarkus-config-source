package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class OptionalPrivateEndpointsClowderPropertyHandler extends EndpointsClowderPropertyHandler {

    private static final String CLOWDER_OPTIONAL_PRIVATE_ENDPOINTS = "clowder.optional-private-endpoints.";

    public OptionalPrivateEndpointsClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public String handle(String property, ClowderConfigSource configSource) {
        try {
            if (clowderConfig.privateEndpoints == null) {
                configSource.getLogger().infof("No private endpoints section found. Returning empty string for the \"%s\" configuration key", property);
                return "";
            }

            return this.processEndpoints(property, configSource, clowderConfig.privateEndpoints, "Private endpoint");
        } catch (IllegalStateException e) {
            configSource.getLogger().errorf("Failed to load config key '%s' from the Clowder configuration: %s", property, e.getMessage());
            throw e;
        }
    }

    @Override
    protected String getPropertyEndpointKey() {
        return CLOWDER_OPTIONAL_PRIVATE_ENDPOINTS;
    }
}
