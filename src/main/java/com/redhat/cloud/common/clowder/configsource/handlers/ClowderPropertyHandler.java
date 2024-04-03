package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

import java.util.List;

public abstract class ClowderPropertyHandler {

    protected final ClowderConfig clowderConfig;

    protected ClowderPropertyHandler(ClowderConfig clowderConfig) {
        this.clowderConfig = clowderConfig;
    }

    public abstract boolean handles(String property);

    public abstract String handle(String property, ClowderConfigSource configSource);

    /**
     * List of properties that this property handler exposes.
     */
    public List<String> provides() {
        return List.of();
    }
}
