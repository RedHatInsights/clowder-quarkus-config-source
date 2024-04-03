package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class WebPortClowderPropertyHandler extends ClowderPropertyHandler {
    private static final String QUARKUS_HTTP_PORT = "quarkus.http.port";

    public WebPortClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    public boolean handles(String property) {
        return property.equals(QUARKUS_HTTP_PORT);
    }

    public String handle(String property, ClowderConfigSource configSource) {
        return String.valueOf(clowderConfig.webPort);
    }
}
