package com.redhat.cloud.common.clowder.configsource;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class ConfigSourceFactoryTest {

    @BeforeAll
    static void init() {
        System.setProperty("acg.config","target/test-classes/cdappconfig.json");
    }

    @Test
    void testSourceExists() {
        Config config = ConfigProvider.getConfig();

        for (ConfigSource configSource : config.getConfigSources()) {
            if (configSource.getName().equals(ClowderConfigSource.CLOWDER_CONFIG_SOURCE)) {
                return;
            }
        }
        Assertions.fail("Source not found");
    }

    // This is to make sure the right source is used
    // Source-specific tests are in ConfigSourceTest
    @Test
    void testHttpPort() {
        Config config = ConfigProvider.getConfig();
        Integer port = config.getValue("quarkus.http.port",Integer.class);
        Assertions.assertEquals(8000, port);
    }
}
