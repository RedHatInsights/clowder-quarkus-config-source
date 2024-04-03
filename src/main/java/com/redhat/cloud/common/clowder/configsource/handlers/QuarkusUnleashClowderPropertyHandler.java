package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class QuarkusUnleashClowderPropertyHandler extends ClowderPropertyHandler {
    private static final String QUARKUS_UNLEASH = "quarkus.unleash.";

    public QuarkusUnleashClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    public boolean handles(String property) {
        return property.startsWith(QUARKUS_UNLEASH);
    }

    public String handle(String property, ClowderConfigSource configSource) {
        if (clowderConfig.featureFlags == null) {
            configSource.getLogger().warn("Unleash configuration requested by Quarkus but not found the Clowder configuration");
        } else {
            String item = property.substring(QUARKUS_UNLEASH.length());
            if (item.equals("token")) {
                return clowderConfig.featureFlags.clientAccessToken;
            }
            if (item.equals("url")) {
                String url = String.format("%s://%s", clowderConfig.featureFlags.scheme, clowderConfig.featureFlags.hostname);
                if (clowderConfig.featureFlags.port != null) {
                    url += ":" + clowderConfig.featureFlags.port;
                }
                url += "/api";
                return url;
            }
        }

        return configSource.getExistingValue(property);
    }
}
