package com.redhat.cloud.common.clowder.configsource;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class ConfigSourceFactoryTest {

    @Test
    void testSourceExists() {
        Config config = ConfigProvider.getConfig();

        for (ConfigSource configSource : config.getConfigSources()) {
            System.out.println(configSource.getName() + " -> " + configSource.getOrdinal());
            if (configSource.getName().equals(ClowderConfigSource.CLOWDER_CONFIG_SOURCE)) {
                return;
            }
        }
        Assertions.fail("Source not found");
    }
}
