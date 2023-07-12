package com.redhat.cloud.common.clowder.configsource;

import io.smallrye.config.ConfigValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigSourceTest {

    private static final Pattern VERIFY_FULL_URL_PATTERN = Pattern.compile("(jdbc:(tracing:|otel:)?)?postgresql://some.host:15432/some-db\\?sslmode=verify-full&sslrootcert=(.+rds-ca-root.+\\.crt)");
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
        assertEquals("ephemeral-host.svc:29092", ccs.getValue("kafka.bootstrap.servers"));
        assertEquals("ephemeral-host.svc:29092", ccs.getValue("camel.component.kafka.brokers"));
    }

    @Test
    void testKafkaBootstrapServers() {
        ClowderConfigSource ccs2 = new ClowderConfigSource("target/test-classes/cdappconfig2.json", APP_PROPS_MAP);
        assertEquals("ephemeral-host.svc:29092,other-host.svc:39092", ccs2.getValue("kafka.bootstrap.servers"));
        assertEquals("ephemeral-host.svc:29092,other-host.svc:39092", ccs2.getValue("camel.component.kafka.brokers"));
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

    @Test
    void testSecuredEndpoint() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        ClowderConfigSource cc = new ClowderConfigSource("target/test-classes/cdappconfig_secured_endpoint.json", APP_PROPS_MAP);

        assertEquals("https://n-api.svc:9999", cc.getValue("clowder.endpoints.notifications-api.url"));

        String path = cc.getValue("clowder.endpoints.notifications-api.trust-store-path");
        String password = cc.getValue("clowder.endpoints.notifications-api.trust-store-password");
        String type = cc.getValue("clowder.endpoints.notifications-api.trust-store-type");

        assertNotNull(path);
        assertNotNull(password);
        assertNotNull(type);

        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(new FileInputStream(path), password.toCharArray());

        assertEquals(1, Collections.list(keyStore.aliases()).size());
    }

    @Test
    void testSecuredEndpointMultipleCert() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        ClowderConfigSource cc = new ClowderConfigSource("target/test-classes/cdappconfig_secured_endpoint_multiple_cert.json", APP_PROPS_MAP);

        assertEquals("https://n-api.svc:9999", cc.getValue("clowder.endpoints.notifications-api.url"));

        String path = cc.getValue("clowder.endpoints.notifications-api.trust-store-path");
        String password = cc.getValue("clowder.endpoints.notifications-api.trust-store-password");
        String type = cc.getValue("clowder.endpoints.notifications-api.trust-store-type");

        assertNotNull(path);
        assertNotNull(password);
        assertNotNull(type);

        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(new FileInputStream(path), password.toCharArray());

        assertEquals(3, Collections.list(keyStore.aliases()).size());
    }

    @Test
    void testWhenTlsPortIsOff() {
        ClowderConfigSource cc = new ClowderConfigSource("target/test-classes/cdappconfig_tls_is_off.json", APP_PROPS_MAP);

        assertEquals("http://n-api.svc:9999", cc.getValue("clowder.endpoints.notifications-api.url"));
        assertNull(cc.getValue("clowder.endpoints.notifications-api.trust-store-path"));
        assertNull(cc.getValue("clowder.endpoints.notifications-api.trust-store-password"));
        assertNull(cc.getValue("clowder.endpoints.notifications-api.trust-store-type"));
    }

    @Test
    void singleCertificateParse() throws IOException {
        String certContent = Files.readString(new File("target/test-classes/cert01.pem").toPath());
        List<String> certs = ClowderConfigSource.readCerts(certContent);

        assertNotNull(certs);
        assertEquals(1, certs.size());
        assertEquals("MIIFgTCCA2mgAwIBAgIJAO8lZ2x+wQ1VMA0GCSqGSIb3DQEBCwUAMFcxCzAJBgNVBAYTAlhYMRAwDgYDVQQIDAd1bmtub3duMRAwDgYDVQQHDAd1bmtub3duMRAwDgYDVQQKDAd1bmtub3duMRIwEAYDVQQDDAlsb2NhbGhvc3QwHhcNMjMwMzA2MTg1NzA0WhcNMjMwNDA1MTg1NzA0WjBXMQswCQYDVQQGEwJYWDEQMA4GA1UECAwHdW5rbm93bjEQMA4GA1UEBwwHdW5rbm93bjEQMA4GA1UECgwHdW5rbm93bjESMBAGA1UEAwwJbG9jYWxob3N0MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAv0SlzA9wrmEXLboFFn49lBMMbK1BJANSxno656VslrK7Mq4A9cF+qDjSOFDHNCgaHl/4oqIpIKa2/RUqeXKs2qrSakZAOte6Cw4m3sLEIWDWdsoaCH4bRuOt6nQwDmPfTtLlJcU2yBvOJoeaGfsKreaInKq6eR91qWqUbVBzSpxLIyolgp9vyurPvCPYreotsqWvQnUQcYtW9C2JJ+0Xwyb7Zon5JVBkwHIn7z4aISiyJuYcu1NmrmAjQLl8+SsUHhZ1iIrhekEFMinoQ+QdMA0mmSX8QszfQqAvYrsD652nZ9tCVs2xDt1rOLRL3JDu4K96kua4ApEADoc2KYbErssbyXUPrVlJe/5XVnId99/l7RLLpnZV61iWr2JkZUHZaEF3fYs4dKJdQELeB9biBgi/VkO81XPG+DzBxrEpRPbVeQF9jGEQ6CGNXss6q5qdxAPjmAskCMr2gKaQCz3JICPjR0Cz5NqbwVMyXnFbxi5oLpms44NCDWfXXGG7y16pDUqGQE/UTvQD3y2z/Br4aUB8utGUwiL/m3W96L3JxK48BQlJ+eCuRFlWxcqmHKmdDu5wB9hQQnE4ihfh0pw/IeKodJD7b1Oj/L+MjtYM+e/+q8rMwEVEwnFpOqhtA1kVA5oLgdI8+L0kMVi7G78qAGnMYh7q8f5OOUonXy71shMCAwEAAaNQME4wHQYDVR0OBBYEFBQoyqB/pUPICm/mEGCYs3CNzLvNMB8GA1UdIwQYMBaAFBQoyqB/pUPICm/mEGCYs3CNzLvNMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQELBQADggIBAKOPOY1i481G3ub2BJiupKzUMxnvssWysx1k2TQSc73ApXef3hjFbllH2TD3kD+86BopUljxoI0fkoh8PkKyaMX5N42Aq5GQQhUhuKBvvcgKRM6xqTQEZbjZMCb0zS1PEDPOlNbGhOhQPSSXQ9z4uH6ISuPCmNUhp+plJe2BqV+gUpTKBMkCka+jjoJokcgRq7tMIRuN+fNA/Hn6mAqMslb+CPrfWY0yarJod2RA9pc4JjZii7azjc5crttRwfHJGWRSZcLLYm+wkMCYxeCoOosprrtAfKE3OzfM9lRLrg3C1YwVLWPJJleTVzv6qcZm3EI7PRARIfw1tGky0YavsTIcmOjPO3rCw01FeIYyBnECxoX0UOapRaHbDQ/PJIFXV2x/hrLe2g/p/fJv7OTUqVcV3rAkolmbryNQCOlXkojBTw7CcQLECaBRLPBZv+vezs9WE+AfvGN6RcPzVIGNi7ItdiN6PPYDj2csiLRQtfpqzMmY3mER9M58JZknwreS0kNKx/Z2txYBjhtkHHEuNaIkcb2ePNqkLj7UHPxjWb6nvx80t0bC0V9OllmkQ9jjdb1KGbpgepYeDDSg3/1M+2uTu2oJhi+AWB86ozJpooMmMvyonZWJz2jRpCudWHcvy3lOPPIDjojebuHaBQT6btPccxaCO2ysEg1k9VgNa4M0", certs.get(0));
    }

    @Test
    void multipleCertificateParse() throws IOException {
        String certContent = Files.readString(new File("target/test-classes/cert02.pem").toPath());
        List<String> certs = ClowderConfigSource.readCerts(certContent);

        assertNotNull(certs);
        assertEquals(3, certs.size());

        assertEquals("MIIFgTCCA2mgAwIBAgIJAO8lZ2x+wQ1VMA0GCSqGSIb3DQEBCwUAMFcxCzAJBgNVBAYTAlhYMRAwDgYDVQQIDAd1bmtub3duMRAwDgYDVQQHDAd1bmtub3duMRAwDgYDVQQKDAd1bmtub3duMRIwEAYDVQQDDAlsb2NhbGhvc3QwHhcNMjMwMzA2MTg1NzA0WhcNMjMwNDA1MTg1NzA0WjBXMQswCQYDVQQGEwJYWDEQMA4GA1UECAwHdW5rbm93bjEQMA4GA1UEBwwHdW5rbm93bjEQMA4GA1UECgwHdW5rbm93bjESMBAGA1UEAwwJbG9jYWxob3N0MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAv0SlzA9wrmEXLboFFn49lBMMbK1BJANSxno656VslrK7Mq4A9cF+qDjSOFDHNCgaHl/4oqIpIKa2/RUqeXKs2qrSakZAOte6Cw4m3sLEIWDWdsoaCH4bRuOt6nQwDmPfTtLlJcU2yBvOJoeaGfsKreaInKq6eR91qWqUbVBzSpxLIyolgp9vyurPvCPYreotsqWvQnUQcYtW9C2JJ+0Xwyb7Zon5JVBkwHIn7z4aISiyJuYcu1NmrmAjQLl8+SsUHhZ1iIrhekEFMinoQ+QdMA0mmSX8QszfQqAvYrsD652nZ9tCVs2xDt1rOLRL3JDu4K96kua4ApEADoc2KYbErssbyXUPrVlJe/5XVnId99/l7RLLpnZV61iWr2JkZUHZaEF3fYs4dKJdQELeB9biBgi/VkO81XPG+DzBxrEpRPbVeQF9jGEQ6CGNXss6q5qdxAPjmAskCMr2gKaQCz3JICPjR0Cz5NqbwVMyXnFbxi5oLpms44NCDWfXXGG7y16pDUqGQE/UTvQD3y2z/Br4aUB8utGUwiL/m3W96L3JxK48BQlJ+eCuRFlWxcqmHKmdDu5wB9hQQnE4ihfh0pw/IeKodJD7b1Oj/L+MjtYM+e/+q8rMwEVEwnFpOqhtA1kVA5oLgdI8+L0kMVi7G78qAGnMYh7q8f5OOUonXy71shMCAwEAAaNQME4wHQYDVR0OBBYEFBQoyqB/pUPICm/mEGCYs3CNzLvNMB8GA1UdIwQYMBaAFBQoyqB/pUPICm/mEGCYs3CNzLvNMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQELBQADggIBAKOPOY1i481G3ub2BJiupKzUMxnvssWysx1k2TQSc73ApXef3hjFbllH2TD3kD+86BopUljxoI0fkoh8PkKyaMX5N42Aq5GQQhUhuKBvvcgKRM6xqTQEZbjZMCb0zS1PEDPOlNbGhOhQPSSXQ9z4uH6ISuPCmNUhp+plJe2BqV+gUpTKBMkCka+jjoJokcgRq7tMIRuN+fNA/Hn6mAqMslb+CPrfWY0yarJod2RA9pc4JjZii7azjc5crttRwfHJGWRSZcLLYm+wkMCYxeCoOosprrtAfKE3OzfM9lRLrg3C1YwVLWPJJleTVzv6qcZm3EI7PRARIfw1tGky0YavsTIcmOjPO3rCw01FeIYyBnECxoX0UOapRaHbDQ/PJIFXV2x/hrLe2g/p/fJv7OTUqVcV3rAkolmbryNQCOlXkojBTw7CcQLECaBRLPBZv+vezs9WE+AfvGN6RcPzVIGNi7ItdiN6PPYDj2csiLRQtfpqzMmY3mER9M58JZknwreS0kNKx/Z2txYBjhtkHHEuNaIkcb2ePNqkLj7UHPxjWb6nvx80t0bC0V9OllmkQ9jjdb1KGbpgepYeDDSg3/1M+2uTu2oJhi+AWB86ozJpooMmMvyonZWJz2jRpCudWHcvy3lOPPIDjojebuHaBQT6btPccxaCO2ysEg1k9VgNa4M0", certs.get(0));
        assertEquals("MIIFgTCCA2mgAwIBAgIJAPWS//Ai2FLxMA0GCSqGSIb3DQEBCwUAMFcxCzAJBgNVBAYTAlhYMRAwDgYDVQQIDAd1bmtub3duMRAwDgYDVQQHDAd1bmtub3duMRAwDgYDVQQKDAd1bmtub3duMRIwEAYDVQQDDAlsb2NhbGhvc3QwHhcNMjMwMzA2MTcyNDU2WhcNMjMwNDA1MTcyNDU2WjBXMQswCQYDVQQGEwJYWDEQMA4GA1UECAwHdW5rbm93bjEQMA4GA1UEBwwHdW5rbm93bjEQMA4GA1UECgwHdW5rbm93bjESMBAGA1UEAwwJbG9jYWxob3N0MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEArEaE0E9XdzER7eqbOwsHad2zHygF06YfO87wrbJmae+CplZJWDzo/9KwhbvmhUuJ5Q5WNG5gOzuogw+xUGZ4Hr2uyD0JMhBVyipo+sh+Y3zdAHnogzKBS/OouKbLC3bBeVuzh51vKbaTB8jWqyRPVkgFlDng6B+1cdwCeTr8pRvfNb4EVUFMTSbspnn+mMzPaZqvBwl/T/kGUgg9rmZjGWfRpNEs5/IU+of/r+ak8UZbU2G+luml6rpyzEBwVTNx35JwvBQgWuPfZm/k1LsF9OXiQU+E+Cz4DPzVAGIgPvzu2FYr+onYq09aQ75WtH6/LlRDTVexy4VQrJA6Xwd/l6pYToHgOjlTXR8lbgkGgMvWWVgXCUNz5E+6GDgKxK5snfWSufF57zR1HrxbdW76irXPMVYaXxQ/gj8dts1xZnyE5/2AYvJT4sMEalGEqgt3SDMjRqBsKr0frsHZ0o1oJMjChc37HFiYPNaAaZdTZ37bOQp0So0ex0NVdFaWjSJfgIpNXzPxYIoHNuJHlvduAMPnK1dh13A8U2gC9szNwLi7TYq82MqhRYbf/qcv10sZNo2iUsCBlO9zB6Y9lpiY2Qmht7lJ2o4f2Ke1TdBm1y6o5MQQWbBo5nP2j37PZJS5qz8LJaIzSaCuX5fs7ifnHaho9uxjVtKocf7qI7qBCXcCAwEAAaNQME4wHQYDVR0OBBYEFMEvvXfdRUBi0GmJVALBk3Onn0W8MB8GA1UdIwQYMBaAFMEvvXfdRUBi0GmJVALBk3Onn0W8MAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQELBQADggIBAAev6v4un8KSPpwbDE1W6CR9wiUR6RNo+dlgtdQFQKufe/d3A8TbZRiKQZVy/ez1Zi00qHAY7wnGZQejwfVJYgwtKX/kE1wNnbO3t6LP7Iz3JeBo8/pa8Ajf+n6YX5YlJ5IbUVsX4pkyITHvjOUuPm940He8jr4aVqf7HT9K0drgRAX657V4ci+psEvZSBjUFItUrv2xpfK0yykMSrUFulKF5gfJvCVoWBcqMLazaTPMnBWCwgndi9mvE+N345l05aK+5Jxu6piewEcPiKyu1OAGWvlqV9/fj8KmrWSJXM26JwKmUxlewtB3TzLfOGWHeFYSrEdWva/jxO8ZgxZJoM68AQQbUqZyXGHwLzZ0nlF/94051WJK+YZ+4d1397LvYeOpcxb0qiA/xxGhBspV8llgUmdPW090C4LeU14nZK88p3gSFVW/NZ1Mrxl2XnnL2EjerkWRmFAxPe66QSPUGFGfsSvUeGB8BsKAxs3bovQBqw/UJ8aWfJNEQOSjKR0qkwl5hs910dluzxRQW7IrH676LimyGMbADbI0JxzT4JdT2nFyJnSFXXKPhybr6fs85fgb/dYZrn0cl/KcAddEiBNT9f5KHRFQDr31tu10TU3RGa1TR8r9aoelUFhK3d0ECspPw9iGmjOFup7cIsSJYk+0Aefwnxi9ca0OXKUCVxYs", certs.get(1));
        assertEquals("MIIFgTCCA2mgAwIBAgIJAIGzkhzYA2UMMA0GCSqGSIb3DQEBCwUAMFcxCzAJBgNVBAYTAlhYMRAwDgYDVQQIDAd1bmtub3duMRAwDgYDVQQHDAd1bmtub3duMRAwDgYDVQQKDAd1bmtub3duMRIwEAYDVQQDDAlsb2NhbGhvc3QwHhcNMjMwMzA2MTcwNTIwWhcNMjMwNDA1MTcwNTIwWjBXMQswCQYDVQQGEwJYWDEQMA4GA1UECAwHdW5rbm93bjEQMA4GA1UEBwwHdW5rbm93bjEQMA4GA1UECgwHdW5rbm93bjESMBAGA1UEAwwJbG9jYWxob3N0MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAymTDYToVyWUvV0ioRFbp9uZ8WOiC88D1Nx4JX0VR29q1orvtF2+P+/4/sAwlYPojS4VhmVsQPbS7bZVZ022N7jno3p7ZYTyd08nnW3QP51W7oJmzypjuOnG/kHvMtgzlQi5Irz8ka7n9regLqiIHvtdGG+ft1O1snutyxvpi2BAuW/F3QUwjIECXoKzmuQtVIRuemt+pFHQp8NB3q80SpHLelG11bBNOAESCDDszN5tnzIcDxOxO3QuhclHKwfiRF6R2vGTVGLrGbmWrRU08A1VP/rA9cU+bfb8jPBDvxBmPwSGWpr/6IikLtpYyuFF8AzHOTELAgnGNP3FLQNVZ+s4Gx4nHMxeEg9X+xFx3x217lTizZ8JOn+kFoT8bmsQoMj0ZDc/grR5RH6R1mho4wjog6SmvuCJpqARyioWyyLdcj8xKAdBQZ6ZVOWf5jQ0Z09kS5KVXFLxS1cL/kbMkGev1ityX+w+zQcgHFO1PswC10VCkpVo1ypurVpWjvEJYwpdm3UhawK76OLTBeHyuejSNlH/u1gybHLsPImUgI4svUqZTZqBgcKwIXzpAbhsa7da+IOVlIPUlGbDpMmrprw/9+uNu9Lrq7mszoJjDzgoSGY6HJW3QmDX9/KBP9p8GhbkZA5cMKfj1dAdQrvK1C72yEdi13FR/FqKtamixPHcCAwEAAaNQME4wHQYDVR0OBBYEFCNk8S9w77ADF8oukI/4QkuelbQ1MB8GA1UdIwQYMBaAFCNk8S9w77ADF8oukI/4QkuelbQ1MAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQELBQADggIBABh/jJlqLCyUOJ4fypmihWcWLooQINBeeuvhoiEFYxI16f+0jVmJI0FwEjRyz8FM2jxvXJnr3ErhzLBNI+jqTyzU2Dr/gD2ZyU3RFv8X+l9x3cSXNUHYk/y53V6/6GyQU06OQcoHi+ksawppdhpfn/G8ep8aB/yF2krL/JRuq8igXvKuwCUjgAFKQk27UcKDiGLfmvtivBpLqjniqF8C27RnaDVIOw/sZB8G1xVzBzRGawdm5VNKLQv2GrIoxySSAtEdjyCKEkUWkmDlpmQQM6ZWAGx/+G6y7KWMLCWXCn6rWtPl10ucUjm+cVycox4a3bB5OCTo0ws5jK4AUYxrq2VI0ISxnc3RUhXLlctOV7eD2jlmKctp6dTFYenQqwQ2nBYOu0wwTbd57MrFz3L1xr83bagjk2zjEakg4bdOK/KZdSfW1cTznSF9/SKr3LXvV6ux1oD8qtfwFOKCyba2qYycnghQ9Ev50yyXt3mM/IhbfQ7EAfir/m702wyqpc4lR1RjDB4Y+P7z/RRLUSMpAd0Lc3zsh+KQYLrMlQsRKPFzzJqSnmf7slyDqRTrDrtKWASldaZmllqDGwmbG+ygEtdt0SXRol9kg0X+QtykCtuMGSrpKt0qu9wV0Fc7mnJDRtW65OjHGUSDrYFUorRwl3ijzgPe4zaH9I1P6xJ1qL8D", certs.get(2));
    }

    /**
     * Tests that both regular and private endpoints are correctly read.
     */
    @Test
    void endpointsAndPrivateEndpoints() {
        final ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig5.json", APP_PROPS_MAP);

        // Read the regular endpoints.
        assertEquals("http://notifications-api.svc:9876", source.getValue("clowder.endpoints.notifications-api.url"));
        assertEquals("http://notifications-gateway.svc:1234", source.getValue("clowder.endpoints.notifications-gateway.url"));

        // Read the private endpoints.
        assertEquals("http://notifications-engine.svc:5555", source.getValue("clowder.private-endpoints.notifications-engine.url"));
    }

    /**
     * Tests that the URL value contains the "https" prefix and that the key
     * store with the single certificate gets created.
     */
    @Test
    void testSecuredPrivateEndpoint() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        ClowderConfigSource cc = new ClowderConfigSource("target/test-classes/cdappconfig_secured_private_endpoint.json", APP_PROPS_MAP);

        assertEquals("https://notifications-api.svc:9876", cc.getValue("clowder.private-endpoints.notifications-api.url"));

        final String path = cc.getValue("clowder.private-endpoints.notifications-api.trust-store-path");
        final String password = cc.getValue("clowder.private-endpoints.notifications-api.trust-store-password");
        final String type = cc.getValue("clowder.private-endpoints.notifications-api.trust-store-type");

        assertNotNull(path);
        assertNotNull(password);
        assertNotNull(type);

        final KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(new FileInputStream(path), password.toCharArray());

        assertEquals(1, Collections.list(keyStore.aliases()).size());
    }

    /**
     * Tests that the URL value contains the "https" prefix and that the key
     * store with multiple certificates gets created.
     */
    @Test
    void testSecuredPrivateEndpointMultipleCert() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        final ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig_secured_private_endpoint_multiple_cert.json", APP_PROPS_MAP);

        assertEquals("https://notifications-api.svc:9876", source.getValue("clowder.private-endpoints.notifications-api.url"));

        final String path = source.getValue("clowder.private-endpoints.notifications-api.trust-store-path");
        final String password = source.getValue("clowder.private-endpoints.notifications-api.trust-store-password");
        final String type = source.getValue("clowder.private-endpoints.notifications-api.trust-store-type");

        assertNotNull(path);
        assertNotNull(password);
        assertNotNull(type);

        final KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(new FileInputStream(path), password.toCharArray());

        assertEquals(3, Collections.list(keyStore.aliases()).size());
    }

    /**
     * Tests that optional private endpoints are correctly read.
     */
    @Test
    void testOptionalPrivateEndpoints() {
        final ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig5.json", APP_PROPS_MAP);

        // Read the optional private endpoints.
        assertEquals("http://notifications-engine.svc:5555", source.getValue("clowder.optional-private-endpoints.notifications-engine.url"));
    }

    /**
     * Tests that when the "private endpoints" configuration is missing for
     * an optional configuration key, the empty string is returned.
     */
    @Test
    void testOptionalPrivateEndpointEmptyString() {
        final ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig.json", APP_PROPS_MAP);

        // The returned value should be the empty string.
        assertEquals("", source.getValue("clowder.optional-private-endpoints.notifications-engine.url"));
    }

    /**
     * Tests that the URL value contains the "https" prefix and that the key
     * store with the single certificate gets created when an optional private
     * endpoint is specified in the configuration key.
     */
    @Test
    void testSecuredOptionalPrivateEndpoint() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        ClowderConfigSource cc = new ClowderConfigSource("target/test-classes/cdappconfig_secured_private_endpoint.json", APP_PROPS_MAP);

        assertEquals("https://notifications-api.svc:9876", cc.getValue("clowder.optional-private-endpoints.notifications-api.url"));

        final String path = cc.getValue("clowder.optional-private-endpoints.notifications-api.trust-store-path");
        final String password = cc.getValue("clowder.optional-private-endpoints.notifications-api.trust-store-password");
        final String type = cc.getValue("clowder.optional-private-endpoints.notifications-api.trust-store-type");

        assertNotNull(path);
        assertNotNull(password);
        assertNotNull(type);

        final KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(new FileInputStream(path), password.toCharArray());

        assertEquals(1, Collections.list(keyStore.aliases()).size());
    }

    /**
     * Tests that the URL value contains the "https" prefix and that the key
     * store with multiple certificates gets created when an optional private
     * endpoint is specified in the configuration key.
     */
    @Test
    void testSecuredOptionalPrivateEndpointMultipleCert() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        final ClowderConfigSource source = new ClowderConfigSource("target/test-classes/cdappconfig_secured_private_endpoint_multiple_cert.json", APP_PROPS_MAP);

        assertEquals("https://notifications-api.svc:9876", source.getValue("clowder.optional-private-endpoints.notifications-api.url"));

        final String path = source.getValue("clowder.optional-private-endpoints.notifications-api.trust-store-path");
        final String password = source.getValue("clowder.optional-private-endpoints.notifications-api.trust-store-password");
        final String type = source.getValue("clowder.optional-private-endpoints.notifications-api.trust-store-type");

        assertNotNull(path);
        assertNotNull(password);
        assertNotNull(type);

        final KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(new FileInputStream(path), password.toCharArray());

        assertEquals(3, Collections.list(keyStore.aliases()).size());
    }

    /**
     * Tests that the requested optional private endpoints' parameters are
     * empty strings.
     */
    @Test
    void testSecuredOptionalPrivateEndpointEmptyString() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        ClowderConfigSource cc = new ClowderConfigSource("target/test-classes/cdappconfig.json", APP_PROPS_MAP);

        assertEquals("", cc.getValue("clowder.optional-private-endpoints.notifications-api.url"));

        assertEquals("", cc.getValue("clowder.optional-private-endpoints.notifications-api.trust-store-path"));
        assertEquals("", cc.getValue("clowder.optional-private-endpoints.notifications-api.trust-store-password"));
        assertEquals("", cc.getValue("clowder.optional-private-endpoints.notifications-api.trust-store-type"));
    }
}
