package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class QuarkusLogCloudWatchClowderPropertyHandler extends ClowderPropertyHandler {

    private static final String QUARKUS_LOG_CLOUDWATCH = "quarkus.log.cloudwatch";

    public QuarkusLogCloudWatchClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public boolean handles(String property) {
        return property.startsWith(QUARKUS_LOG_CLOUDWATCH);
    }

    @Override
    public String handle(String property, ClowderConfigSource configSource) {
        if (clowderConfig.logging == null) {
            throw new IllegalStateException("No logging section found");
        }
        if (clowderConfig.logging.cloudwatch == null) {
            throw new IllegalStateException("No cloudwatch section found in logging object");
        }

        // Check for not null type and not "null" provider to enable and read
        // cloudwatch properties from Clowder config.
        // Note that the "null" type is a Clowder logging provider that disables
        // central logging.
        // Empty string type has to be treated as cloudwatch, as one of the Clowder
        // logging providers (appinterface) did not set it correctly.
        if (clowderConfig.logging.type != null && !clowderConfig.logging.type.equals("null")) {
            int prefixLen = QUARKUS_LOG_CLOUDWATCH.length();
            String sub = property.substring(prefixLen + 1);
            switch (sub) {
                case "access-key-id":
                    return clowderConfig.logging.cloudwatch.accessKeyId;
                case "access-key-secret":
                    return clowderConfig.logging.cloudwatch.secretAccessKey;
                case "region":
                    return clowderConfig.logging.cloudwatch.region;
                case "log-group":
                    return clowderConfig.logging.cloudwatch.logGroup;
                default:
                    // fall through to fetching the value from application.properties
            }
        } else {
            // treat null logging type as disabled CloudWatch logging
            if (property.equals(QUARKUS_LOG_CLOUDWATCH + ".enabled")) {
                return "false";
            }
        }

        return configSource.getExistingValue(property);
    }
}
