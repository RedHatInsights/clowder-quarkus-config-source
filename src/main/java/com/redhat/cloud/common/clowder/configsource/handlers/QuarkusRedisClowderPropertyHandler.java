package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class QuarkusRedisClowderPropertyHandler extends ClowderPropertyHandler {
    private static final String QUARKUS_REDIS = "quarkus.redis.";

    public QuarkusRedisClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public boolean handles(String property) {
        return property.startsWith(QUARKUS_REDIS);
    }

    @Override
    public String handle(String property, ClowderConfigSource configSource) {
        if (clowderConfig.inMemoryDb == null) {
            throw new IllegalStateException("No inMemoryDb section found");
        }

        String sub = property.substring(QUARKUS_REDIS.length());

        return switch (sub) {
            case "hosts" -> "redis://" + clowderConfig.inMemoryDb.hostname + ":" + clowderConfig.inMemoryDb.port;
            case "password" ->
                    clowderConfig.inMemoryDb.password; // Note: This value will be overriden by a password provided in `quarkus.redis.hosts`.
            default ->
                    configSource.getExistingValue(property); // fallback to fetching the value from application.properties
        };
    }
}
