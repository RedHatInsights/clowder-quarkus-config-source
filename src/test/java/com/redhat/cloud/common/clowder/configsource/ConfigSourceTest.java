package com.redhat.cloud.common.clowder.configsource;

import io.smallrye.config.ConfigValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.KAFKA_SASL_JAAS_CONFIG_KEY;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.KAFKA_SASL_MECHANISM_KEY;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.KAFKA_SECURITY_PROTOCOL_KEY;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.KAFKA_SSL_TRUSTSTORE_LOCATION_KEY;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.KAFKA_SSL_TRUSTSTORE_TYPE_KEY;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.KAFKA_SSL_TRUSTSTORE_TYPE_VALUE;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.CAMEL_KAFKA_SASL_JAAS_CONFIG_KEY;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.CAMEL_KAFKA_SASL_MECHANISM_KEY;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.CAMEL_KAFKA_SECURITY_PROTOCOL_KEY;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigSourceTest {

    private static final Pattern VERIFY_FULL_URL_PATTERN = Pattern.compile("(jdbc:(tracing:)?)?postgresql://some.host:15432/some-db\\?sslmode=verify-full&sslrootcert=(.+rds-ca-root.+\\.crt)");
    private static final String EXPECTED_CERT = "Dummy value";
    private static final Map<String, ConfigValue> APP_PROPS_MAP = new HashMap<>();
    private static final Properties APP_PROPS = new Properties();

    private static ClowderConfigSource ccs;

    @BeforeAll
    static void setup() throws Exception {

        try (InputStream is = ConfigSourceTest.class.getResourceAsStream("/application.properties")){
            APP_PROPS.load(is);

            APP_PROPS.forEach((k, v) -> {
                        ConfigValue cv = new ConfigValue.ConfigValueBuilder()
                                .withName(String.valueOf(k))
                                .withValue(String.valueOf(v))
                                .withConfigSourceName("PropertiesConfigSource[source=application.properties]")
                                .withConfigSourceOrdinal(250)
                                .build();
                        APP_PROPS_MAP.put((String) k,cv);
                    }
                );
        }

        ccs = new ClowderConfigSource("target/test-classes/cdappconfig.json", APP_PROPS_MAP);
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
        ClowderConfigSource ccs2 = new ClowderConfigSource("target/test-classes/cdappconfig2.json", APP_PROPS_MAP);
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
        if (((String) APP_PROPS.get("quarkus.datasource.jdbc.url")).contains("tracing")) {
            expected += ":tracing";
        }
        expected += ":postgresql://some.host:15432/some-db?sslmode=require";

        assertEquals(expected, url );
    }

    @Test
    void testDatabaseReactive() {
        String url = ccs.getValue("quarkus.datasource.reactive.url");
        assertEquals("postgresql://some.host:15432/some-db", url);

        String sslMode = ccs.getValue("quarkus.datasource.reactive.postgresql.ssl-mode");
        assertEquals("require", sslMode);
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
    void testLogOnEmptyLoggingType() {
        // Tests for a Clowder buggy case where the type is not set
        // for appinterface provider, that in fact sets cloudwatch credentials.
        ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig4.json", APP_PROPS_MAP);
        String value = source.getValue("quarkus.log.cloudwatch.access-key-id");
        assertEquals("my-key-id", value);
        value = source.getValue("quarkus.log.cloudwatch.access-key-secret");
        assertEquals("very-secret", value);
        value = source.getValue("quarkus.log.cloudwatch.region");
        assertEquals("eu-central-1", value);
        value = source.getValue("quarkus.log.cloudwatch.log-group");
        assertEquals("my-log-group", value);
        value = source.getValue("quarkus.log.cloudwatch.log-stream-name");
        assertEquals("my-log-stream", value);
        value = source.getValue("quarkus.log.cloudwatch.level");
        assertEquals("INFO", value);
        value = source.getValue("quarkus.log.cloudwatch.enabled"); // Does not exist.
        assertNull(value);
    }

    @Test
    void testLogNullProvider() {
        ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig2.json", APP_PROPS_MAP);
        String value = source.getValue("quarkus.log.cloudwatch.enabled");
        assertEquals("false", value);
    }

    @Test
    void testNoKafkaSection() {
        ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig3.json", APP_PROPS_MAP);
        assertThrows(IllegalStateException.class, () -> source.getValue("kafka.bootstrap.servers"));
    }

    @Test
    void testNoLogSection() {
        ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig3.json", APP_PROPS_MAP);
        assertThrows(IllegalStateException.class, () -> source.getValue("quarkus.log.cloudwatch.region"));
    }

    @Test
    void testNoDatabaseSection() {
        ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig3.json", APP_PROPS_MAP);
        assertThrows(IllegalStateException.class, () -> source.getValue("quarkus.datasource.username"));
    }

    @Test
    void testClowderEndpoints() {
        assertEquals("http://n-api.svc:8000", ccs.getValue("clowder.endpoints.notifications-api"));
        assertEquals("http://n-gw.svc:8000", ccs.getValue("clowder.endpoints.notifications-gw"));
    }

    @Test
    void testUnknownClowderEndpoint() {
        assertNull(ccs.getValue("clowder.endpoints.unknown"));
    }

    @Test
    void testVerifyFullSslMode() throws IOException {
        ClowderConfigSource ccs2 = new ClowderConfigSource("target/test-classes/cdappconfig_verify-full_valid.json", APP_PROPS_MAP);

        String jdbcUrl = ccs2.getValue("quarkus.datasource.jdbc.url");
        verifyUrlAndCertFile(jdbcUrl);

        String reactiveUrl = ccs2.getValue("quarkus.datasource.reactive.url");
        assertEquals("postgresql://some.host:15432/some-db", reactiveUrl);
        String sslMode = ccs2.getValue("quarkus.datasource.reactive.postgresql.ssl-mode");
        assertEquals("verify-full", sslMode);
        String algorithm = ccs2.getValue("quarkus.datasource.reactive.hostname-verification-algorithm");
        assertEquals("HTTPS", algorithm);
        String pemEnabled = ccs2.getValue("quarkus.datasource.reactive.trust-certificate-pem");
        assertEquals("true", pemEnabled);
        String certs = ccs2.getValue("quarkus.datasource.reactive.trust-certificate-pem.certs");
        assertTrue(certs.endsWith(".crt"));
    }

    private void verifyUrlAndCertFile(String url) throws IOException {
        Matcher matcher = VERIFY_FULL_URL_PATTERN.matcher(url);
        assertTrue(matcher.matches());
        String cert = Files.readString(Path.of(matcher.group(3)), UTF_8);
        assertEquals(EXPECTED_CERT, cert);
    }

    @Test
    void testVerifyFullSslModeWithMissingRdsCa() {
        ClowderConfigSource ccs2 = new ClowderConfigSource("target/test-classes/cdappconfig_verify-full_invalid.json", APP_PROPS_MAP);
        assertThrows(IllegalStateException.class, () -> {
            ccs2.getValue("quarkus.datasource.jdbc.url");
        });
    }

    @Test
    void testKafkaNoAuthtype() {
        assertNull(ccs.getValue(KAFKA_SASL_JAAS_CONFIG_KEY));
        assertNull(ccs.getValue(KAFKA_SASL_MECHANISM_KEY));
        assertNull(ccs.getValue(KAFKA_SECURITY_PROTOCOL_KEY));
        assertNull(ccs.getValue(KAFKA_SSL_TRUSTSTORE_LOCATION_KEY));
        assertNull(ccs.getValue(KAFKA_SSL_TRUSTSTORE_TYPE_KEY));

        assertNull(ccs.getValue(CAMEL_KAFKA_SASL_JAAS_CONFIG_KEY));
        assertNull(ccs.getValue(CAMEL_KAFKA_SASL_MECHANISM_KEY));
        assertNull(ccs.getValue(CAMEL_KAFKA_SECURITY_PROTOCOL_KEY));
        assertNull(ccs.getValue(CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY));
        assertNull(ccs.getValue(CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY));
    }

    @Test
    void testKafkaSaslPlainAuthtype() {
        ClowderConfigSource ccs2 = new ClowderConfigSource("target/test-classes/cdappconfig_kafka_sasl_plain_authtype.json", APP_PROPS_MAP);
        String expJasConfig = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"john\" password=\"doe\";";
        assertEquals(expJasConfig, ccs2.getValue(KAFKA_SASL_JAAS_CONFIG_KEY));
        assertEquals("PLAIN", ccs2.getValue(KAFKA_SASL_MECHANISM_KEY));
        assertEquals("SASL_SSL", ccs2.getValue(KAFKA_SECURITY_PROTOCOL_KEY));
        assertNull(ccs.getValue(KAFKA_SSL_TRUSTSTORE_LOCATION_KEY));
        assertNull(ccs.getValue(KAFKA_SSL_TRUSTSTORE_TYPE_KEY));

        assertEquals(expJasConfig, ccs2.getValue(CAMEL_KAFKA_SASL_JAAS_CONFIG_KEY));
        assertEquals("PLAIN", ccs2.getValue(CAMEL_KAFKA_SASL_MECHANISM_KEY));
        assertEquals("SASL_SSL", ccs2.getValue(CAMEL_KAFKA_SECURITY_PROTOCOL_KEY));
        assertNull(ccs.getValue(CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY));
        assertNull(ccs.getValue(CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY));
    }

    @Test
    void testKafkaSaslScramAuthtype() throws IOException {
        ClowderConfigSource ccs2 = new ClowderConfigSource("target/test-classes/cdappconfig_kafka_sasl_scram_authtype.json", APP_PROPS_MAP);
        String expJasConfig = "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"john\" password=\"doe\";";
        assertEquals(expJasConfig, ccs2.getValue(KAFKA_SASL_JAAS_CONFIG_KEY));
        assertEquals("SCRAM-SHA-512", ccs2.getValue(KAFKA_SASL_MECHANISM_KEY));
        assertEquals("SASL_SSL", ccs2.getValue(KAFKA_SECURITY_PROTOCOL_KEY));
        String truststoreLocation = ccs2.getValue(KAFKA_SSL_TRUSTSTORE_LOCATION_KEY);
        String cert = Files.readString(Path.of(truststoreLocation), UTF_8);
        assertEquals(EXPECTED_CERT, cert);
        assertEquals(KAFKA_SSL_TRUSTSTORE_TYPE_VALUE, ccs2.getValue(KAFKA_SSL_TRUSTSTORE_TYPE_KEY));

        assertEquals(expJasConfig, ccs2.getValue(CAMEL_KAFKA_SASL_JAAS_CONFIG_KEY));
        assertEquals("SCRAM-SHA-512", ccs2.getValue(CAMEL_KAFKA_SASL_MECHANISM_KEY));
        assertEquals("SASL_SSL", ccs2.getValue(CAMEL_KAFKA_SECURITY_PROTOCOL_KEY));
        String camelTruststoreLocation = ccs2.getValue(CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY);
        String camelCert = Files.readString(Path.of(camelTruststoreLocation), UTF_8);
        assertEquals(EXPECTED_CERT, camelCert);
        assertEquals(KAFKA_SSL_TRUSTSTORE_TYPE_VALUE, ccs2.getValue(CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY));
    }

    @Test
    void testKafkaMtlsAuthtype() {
        ClowderConfigSource ccs2 = new ClowderConfigSource("target/test-classes/cdappconfig_kafka_mtls_authtype.json", APP_PROPS_MAP);
        assertNull(ccs2.getValue(KAFKA_SASL_JAAS_CONFIG_KEY));
        assertNull(ccs2.getValue(KAFKA_SASL_MECHANISM_KEY));
        assertNull(ccs2.getValue(KAFKA_SECURITY_PROTOCOL_KEY));
        assertNull(ccs2.getValue(KAFKA_SSL_TRUSTSTORE_LOCATION_KEY));
        assertNull(ccs2.getValue(KAFKA_SSL_TRUSTSTORE_TYPE_KEY));

        assertNull(ccs2.getValue(CAMEL_KAFKA_SASL_JAAS_CONFIG_KEY));
        assertNull(ccs2.getValue(CAMEL_KAFKA_SASL_MECHANISM_KEY));
        assertNull(ccs2.getValue(CAMEL_KAFKA_SECURITY_PROTOCOL_KEY));
        assertNull(ccs.getValue(CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY));
        assertNull(ccs.getValue(CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY));
    }
}
