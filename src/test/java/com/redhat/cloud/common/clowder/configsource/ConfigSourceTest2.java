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
public class ConfigSourceTest2 {

    static Properties appProps;
    static ClowderConfigSource ccs;
    static final Map<String, ConfigValue> appPropsMap = new HashMap<>();

    @BeforeAll
    static void setup() throws Exception {

        appProps = new Properties();
        try (InputStream is = ConfigSourceTest2.class.getResourceAsStream("/application.properties")){
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

        ccs = new ClowderConfigSource("target/test-classes/cdappconfig3.json", appPropsMap);
    }

    @Test
    void testWebPort() {
        String port = ccs.getValue("quarkus.http.port");
        assertEquals("8000",port);
    }

    @Test
    void testKafkaBootstrap() {
        assertThrows(IllegalStateException.class, () -> ccs.getValue("kafka.bootstrap.servers"));

    }
}
