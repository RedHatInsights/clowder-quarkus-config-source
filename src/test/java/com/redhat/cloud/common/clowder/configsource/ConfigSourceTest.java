package com.redhat.cloud.common.clowder.configsource;

import io.smallrye.config.ConfigValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 */
public class ConfigSourceTest {

    static Properties appProps;
    static ClowderConfigSource ccs;
    static final Map<String, ConfigValue> appPropsMap = new HashMap<>();

    @BeforeAll
    static void setup() throws Exception {

        appProps = new Properties();
        try (InputStream is = ConfigSourceTest.class.getResourceAsStream("/application.properties")){
            appProps.load(is);

            appProps.forEach((k,v) -> {
                        ConfigValue cv = new ConfigValue.ConfigValueBuilder()
                                .withName(String.valueOf(k))
                                .withValue(String.valueOf(v))
                                .withConfigSourceName("PropertiesConfigSource[source=application.properties]")
                                .withConfigSourceOrdinal(250)
                                .build();
                        appPropsMap.put((String) k,cv);
                    }
                );
        }

        ccs = new ClowderConfigSource("target/test-classes/cdappconfig.json", appPropsMap);
    }

    @Test
    void testWebPort() {
        String port = ccs.getValue("quarkus.http.port");
        assertEquals("8000",port);
    }

    @Test
    void testKafkaBootstrap() {
        String boostrap = ccs.getValue("kafka.bootstrap.servers");
        assertEquals("ephemeral-host.svc:29092", boostrap);
    }

    @Test
    void testKafkaBootstrapServers() {
        ClowderConfigSource ccs2 = new ClowderConfigSource("target/test-classes/cdappconfig2.json",appPropsMap);
        String boostrap = ccs2.getValue("kafka.bootstrap.servers");
        assertEquals("ephemeral-host.svc:29092,other-host.svc:39092", boostrap);
    }

    @Test
    void testKafkaIncoming() {
        String topic = ccs.getValue("mp.messaging.incoming.ingress.topic");
        assertEquals("platform-tmp-12345", topic);
    }

    @Test
    void testKafkaOutgoing() {
        String topic = ccs.getValue("mp.messaging.outgoing.egress.topic");
        assertEquals("platform-tmp-666", topic);
    }

    @Test
    void testDatabaseCredentials() {
        String user = ccs.getValue("quarkus.datasource.username");
        String pass = ccs.getValue("quarkus.datasource.password");

        assertEquals("aUser",user);
        assertEquals("secret",pass);
    }

    @Test
    void testDatabaseJdbc() {
        String url = ccs.getValue("quarkus.datasource.jdbc.url");
        String expected = "jdbc";
        if (((String)appProps.get("quarkus.datasource.jdbc.url")).contains("tracing")) {
            expected += ":tracing";
        }
        expected += ":postgresql://some.host:15432/some-db?sslmode=require";

        assertEquals(expected, url );
    }

    @Test
    void testDatabaseReactive() {
        String url = ccs.getValue("quarkus.datasource.reactive.url");
        assertEquals("postgresql://some.host:15432/some-db?sslmode=require", url );
    }

    @Test
    void testUnchangedProperty() {
        String value = ccs.getValue("quarkus.http.access-log.category");
        assertEquals("access_log", value);
    }

    @Test
    void testLogCw() {
        String value = ccs.getValue("quarkus.log.cloudwatch.access-key-id");
        assertEquals("my-key-id", value);
        value = ccs.getValue("quarkus.log.cloudwatch.access-key-secret");
        assertEquals("very-secret", value);
        value = ccs.getValue("quarkus.log.cloudwatch.region");
        assertEquals("eu-central-1", value);
        value = ccs.getValue("quarkus.log.cloudwatch.log-group");
        assertEquals("my-log-group", value);
        value = ccs.getValue("quarkus.log.cloudwatch.log-stream-name");
        assertEquals("my-log-stream", value);
        value = ccs.getValue("quarkus.log.cloudwatch.level");
        assertEquals("INFO", value);
        value = ccs.getValue("quarkus.log.cloudwatch.enabled"); // Does not exist.
        assertNull(value);

    }

    @Test
    void testNoKafkaSection() {
        ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig3.json", appPropsMap);
        assertThrows(IllegalStateException.class, () -> source.getValue("kafka.bootstrap.servers"));
    }

    @Test
    void testNoLogSection() {
        ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig3.json", appPropsMap);
        assertThrows(IllegalStateException.class, () -> source.getValue("quarkus.log.cloudwatch.region"));
    }

    @Test
    void testNoDatabaseSection() {
        ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig3.json", appPropsMap);
        assertThrows(IllegalStateException.class, () -> source.getValue("quarkus.datasource.username"));
    }

}
