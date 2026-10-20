package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class PrivateEndpointsClowderPropertyHandler extends EndpointsClowderPropertyHandler {

    private static final String CLOWDER_PRIVATE_ENDPOINTS = "clowder.private-endpoints.";

    public PrivateEndpointsClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public String handle(String property, ClowderConfigSource configSource) {
        try {
            if (clowderConfig.privateEndpoints == null) {
                throw new IllegalStateException("No private endpoints section found");
            }

            return this.processEndpoints(property, configSource, clowderConfig.privateEndpoints, "Private endpoint");
        } catch (IllegalStateException e) {
            configSource.getLogger().errorf("Failed to load config key '%s' from the Clowder configuration: %s", property, e.getMessage());
            throw e;
        }
    }

    @Override
    protected String getPropertyEndpointKey() {
        return CLOWDER_PRIVATE_ENDPOINTS;
    }
}
