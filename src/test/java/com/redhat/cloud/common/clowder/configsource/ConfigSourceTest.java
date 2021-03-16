package com.redhat.cloud.common.clowder.configsource;

import io.smallrye.config.ConfigValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
public class ConfigSourceTest {

    static Properties appProps;
    static ClowderConfigSource ccs;

    @BeforeAll
    static void setup() throws Exception {

        appProps = new Properties();
        Map<String, ConfigValue> appPropsMap = new HashMap<>();
        try (InputStream is = ConfigSourceTest.class.getResourceAsStream("/application.properties")){
            appProps.load(is);

            appProps.forEach((k,v) -> {
                        ConfigValue cv = new ConfigValue.ConfigValueBuilder()
                                .withName(String.valueOf(k))
                                .withValue(String.valueOf(v))
                                .withConfigSourceName("application.properties") // TODO figure name + ordinal from Quarkus
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
}
