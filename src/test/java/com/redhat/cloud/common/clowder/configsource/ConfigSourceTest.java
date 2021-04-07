package com.redhat.cloud.common.clowder.configsource;

import io.smallrye.config.ConfigValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class ConfigSourceTest {

    private static final Pattern VERIFY_FULL_URL_PATTERN = Pattern.compile("(jdbc:(tracing:)?)?postgresql://some.host:15432/some-db\\?sslmode=verify-full sslrootcert=(.+rds-ca-root.+\\.pem)");
    private static final String EXPECTED_CERT =
            "MIIEBjCCAu6gAwIBAgIJAMc0ZzaSUK51MA0GCSqGSIb3DQEBCwUAMIGPMQswCQYD" +
            "VQQGEwJVUzEQMA4GA1UEBwwHU2VhdHRsZTETMBEGA1UECAwKV2FzaGluZ3RvbjEi" +
            "MCAGA1UECgwZQW1hem9uIFdlYiBTZXJ2aWNlcywgSW5jLjETMBEGA1UECwwKQW1h" +
            "em9uIFJEUzEgMB4GA1UEAwwXQW1hem9uIFJEUyBSb290IDIwMTkgQ0EwHhcNMTkw" +
            "ODIyMTcwODUwWhcNMjQwODIyMTcwODUwWjCBjzELMAkGA1UEBhMCVVMxEDAOBgNV" +
            "BAcMB1NlYXR0bGUxEzARBgNVBAgMCldhc2hpbmd0b24xIjAgBgNVBAoMGUFtYXpv" +
            "biBXZWIgU2VydmljZXMsIEluYy4xEzARBgNVBAsMCkFtYXpvbiBSRFMxIDAeBgNV" +
            "BAMMF0FtYXpvbiBSRFMgUm9vdCAyMDE5IENBMIIBIjANBgkqhkiG9w0BAQEFAAOC" +
            "AQ8AMIIBCgKCAQEArXnF/E6/Qh+ku3hQTSKPMhQQlCpoWvnIthzX6MK3p5a0eXKZ" +
            "oWIjYcNNG6UwJjp4fUXl6glp53Jobn+tWNX88dNH2n8DVbppSwScVE2LpuL+94vY" +
            "0EYE/XxN7svKea8YvlrqkUBKyxLxTjh+U/KrGOaHxz9v0l6ZNlDbuaZw3qIWdD/I" +
            "6aNbGeRUVtpM6P+bWIoxVl/caQylQS6CEYUk+CpVyJSkopwJlzXT07tMoDL5WgX9" +
            "O08KVgDNz9qP/IGtAcRduRcNioH3E9v981QO1zt/Gpb2f8NqAjUUCUZzOnij6mx9" +
            "McZ+9cWX88CRzR0vQODWuZscgI08NvM69Fn2SQIDAQABo2MwYTAOBgNVHQ8BAf8E" +
            "BAMCAQYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4EFgQUc19g2LzLA5j0Kxc0LjZa" +
            "pmD/vB8wHwYDVR0jBBgwFoAUc19g2LzLA5j0Kxc0LjZapmD/vB8wDQYJKoZIhvcN" +
            "AQELBQADggEBAHAG7WTmyjzPRIM85rVj+fWHsLIvqpw6DObIjMWokpliCeMINZFV" +
            "ynfgBKsf1ExwbvJNzYFXW6dihnguDG9VMPpi2up/ctQTN8tm9nDKOy08uNZoofMc" +
            "NUZxKCEkVKZv+IL4oHoeayt8egtv3ujJM6V14AstMQ6SwvwvA93EP/Ug2e4WAXHu" +
            "cbI1NAbUgVDqp+DRdfvZkgYKryjTWd/0+1fS8X1bBZVWzl7eirNVnHbSH2ZDpNuY" +
            "0SBd8dj5F6ld3t58ydZbrTHze7JJOd8ijySAp4/kiu9UfZWuTPABzDa/DSdz9Dk/" +
            "zPW4CXXvhLmE02TA9/HeCw3KEHIwicNuEfw=";

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
    void testVerifyFullSslMode() throws IOException {
        ClowderConfigSource ccs2 = new ClowderConfigSource("target/test-classes/cdappconfig_verify-full_valid.json", appPropsMap);

        String jdbcUrl = ccs2.getValue("quarkus.datasource.jdbc.url");
        verifyUrlAndCertFile(jdbcUrl);

        String reactiveUrl = ccs2.getValue("quarkus.datasource.reactive.url");
        verifyUrlAndCertFile(reactiveUrl);
    }

    private void verifyUrlAndCertFile(String url) throws IOException {
        Matcher matcher = VERIFY_FULL_URL_PATTERN.matcher(url);
        assertTrue(matcher.matches());
        String cert = Files.readString(Path.of(matcher.group(3)), StandardCharsets.UTF_8);
        assertEquals(EXPECTED_CERT, cert);
    }

    @Test
    void testVerifyFullSslModeWithMissingRdsCa() {
        ClowderConfigSource ccs2 = new ClowderConfigSource("target/test-classes/cdappconfig_verify-full_invalid.json", appPropsMap);
        assertThrows(IllegalStateException.class, () -> {
            ccs2.getValue("quarkus.datasource.reactive.url");
        });
    }
}
