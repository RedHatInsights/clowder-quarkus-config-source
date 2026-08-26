package com.redhat.cloud.common.clowder.configsource;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ConfigSourceFactoryTest {

    // acg.config must be set as a JVM system property (see the surefire plugin config in pom.xml),
    // not here in @BeforeAll: quarkus-junit's TestConfigProviderResolver snapshots the config
    // before any test code runs, so setting it later has no effect on ConfigProvider.getConfig().

    @Test
    void testSourceExists() {
        for (ConfigSource configSource : ConfigProvider.getConfig().getConfigSources()) {
            if (configSource.getName().equals(ClowderConfigSource.CLOWDER_CONFIG_SOURCE)) {
                return;
            }
        }
        fail("Source not found");
    }

    // This is to make sure the right source is used
    // Source-specific tests are in ConfigSourceTest
    @Test
    void testHttpPort() {
        assertEquals(8000, ConfigProvider.getConfig().getValue("quarkus.http.port", Integer.class));
    }
}
