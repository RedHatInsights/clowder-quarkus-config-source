package com.redhat.cloud.common.clowder.configsource;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercise actual file loading, factory registration, expression expansion, and precedence. */
class DependencyEndpointsFactoryTest {

    @TempDir
    Path temp;

    @Test
    void resolvesV2ExpressionsAndBooleanMetadataThroughFactory() {
        SmallRyeConfig config = config("target/test-classes/cdappconfig_v2.json", Map.of());
        assertEquals("https://rbac.remote.example:443/base", config.getValue("rbac.url", String.class));
        assertTrue(config.getValue("rbac.authenticated", Boolean.class));
        assertEquals("https://export.remote.example:443", config.getValue("export.url", String.class));
        assertTrue(config.getValue("export.authenticated", Boolean.class));
        assertFalse(config.getOptionalValue("rbac.trust-store", String.class).isPresent());
    }

    @Test
    void v1OnlyFileResolvesThroughFactory() {
        SmallRyeConfig config = config("target/test-classes/cdappconfig5.json", Map.of(
                "rbac.url", "${clowder.dependency-endpoints.notifications.api.uri}",
                "rbac.authenticated", "${clowder.dependency-endpoints.notifications.api.authenticated}"));
        assertEquals("http://notifications-api.svc:9876", config.getValue("rbac.url", String.class));
        assertFalse(config.getValue("rbac.authenticated", Boolean.class));
    }

    @Test
    void missingClowderFileKeepsLocalDefaults() {
        SmallRyeConfig config = config(temp.resolve("missing.json").toString(), Map.of());
        assertEquals("http://localhost:8080", config.getValue("rbac.url", String.class));
        assertFalse(config.getValue("rbac.authenticated", Boolean.class));
    }

    @Test
    void absentOptionalEndpointAllowsTypedDefaults() throws Exception {
        Path json = temp.resolve("no-endpoints.json");
        Files.writeString(json, "{}");
        SmallRyeConfig config = config(json.toString(), Map.of());
        assertFalse(config.getValue("export.authenticated", Boolean.class));
        assertFalse(config.getOptionalValue("export.url", String.class).isPresent());
    }

    @Test
    void explicitApplicationOverridesWinOverDiscovery() {
        SmallRyeConfig config = config("target/test-classes/cdappconfig_v2.json", Map.of(
                "rbac.url", "http://localhost:9999", "rbac.authenticated", "false"));
        assertEquals("http://localhost:9999", config.getValue("rbac.url", String.class));
        assertFalse(config.getValue("rbac.authenticated", Boolean.class));
    }

    @Test
    void higherPriorityEndpointPropertyOverrideWins() {
        SmallRyeConfig config = config("target/test-classes/cdappconfig_v2.json", Map.of(
                "clowder.dependency-endpoints.rbac.service.uri", "http://localhost:9998"));
        assertEquals("http://localhost:9998", config.getValue("rbac.url", String.class));
    }

    @Test
    void unknownFieldsAndVersionsDoNotBreakV2Loading() throws Exception {
        Path json = temp.resolve("v2.json");
        Files.writeString(json, """
                {"newField": true, "dependencyEndpoints": {"v3": {}, "v2": {"rbac": {"service": {
                  "uri": "https://rbac.example:443", "authenticated": true, "futureMetadata": 42
                }}}}}
                """);
        SmallRyeConfig config = config(json.toString(), Map.of());
        assertEquals("https://rbac.example:443", config.getValue("rbac.url", String.class));
        assertTrue(config.getValue("rbac.authenticated", Boolean.class));
    }

    private SmallRyeConfig config(String file, Map<String, String> overrides) {
        Map<String, String> properties = new HashMap<>();
        properties.put("acg.config", file);
        properties.put("rbac.url", "${clowder.dependency-endpoints.rbac.service.uri:http://localhost:8080}");
        properties.put("rbac.authenticated", "${clowder.dependency-endpoints.rbac.service.authenticated:false}");
        properties.put("rbac.trust-store", "${clowder.dependency-endpoints.rbac.service.trust-store-path:}");
        properties.put("export.url", "${clowder.optional-private-dependency-endpoints.export-service.service.uri:}");
        properties.put("export.authenticated", "${clowder.optional-private-dependency-endpoints.export-service.service.authenticated:false}");
        return new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(properties, "application.properties", 250))
                .withSources(new PropertiesConfigSource(overrides, "explicit overrides", 400))
                .withSources(new ClowderConfigSourceFactory())
                .build();
    }
}
