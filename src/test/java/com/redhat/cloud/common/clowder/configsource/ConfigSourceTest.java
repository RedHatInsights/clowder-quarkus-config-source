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

        String path = cc.getValue("clowder.endpoints.notifications-api.store-path");
        String password = cc.getValue("clowder.endpoints.notifications-api.store-password");
        String type = cc.getValue("clowder.endpoints.notifications-api.store-type");

        assertNotNull(path);
        assertNotNull(password);
        assertNotNull(type);

        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(new FileInputStream(new File(path)), password.toCharArray());

        assertEquals(1, Collections.list(keyStore.aliases()).size());
    }

    @Test
    void testSecuredEndpointMultipleCert() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        ClowderConfigSource cc = new ClowderConfigSource("target/test-classes/cdappconfig_secured_endpoint_multiple_cert.json", APP_PROPS_MAP);

        assertEquals("https://n-api.svc:9999", cc.getValue("clowder.endpoints.notifications-api.url"));

        String path = cc.getValue("clowder.endpoints.notifications-api.store-path");
        String password = cc.getValue("clowder.endpoints.notifications-api.store-password");
        String type = cc.getValue("clowder.endpoints.notifications-api.store-type");

        assertNotNull(path);
        assertNotNull(password);
        assertNotNull(type);

        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(new FileInputStream(new File(path)), password.toCharArray());

        assertEquals(3, Collections.list(keyStore.aliases()).size());
    }

    @Test
    void singleCertificateParse() throws IOException {
        String certContent = Files.readString(new File("target/test-classes/cert01.pem").toPath());
        List<String> certs = ClowderConfigSource.readCerts(certContent);

        assertNotNull(certs);
        assertEquals(1, certs.size());
        assertEquals("MIIFgTCCA2mgAwIBAgIJAO8lZ2x+wQ1VMA0GCSqGSIb3DQEBCwUAMFcxCzAJBgNV" +
                "BAYTAlhYMRAwDgYDVQQIDAd1bmtub3duMRAwDgYDVQQHDAd1bmtub3duMRAwDgYD" +
                "VQQKDAd1bmtub3duMRIwEAYDVQQDDAlsb2NhbGhvc3QwHhcNMjMwMzA2MTg1NzA0" +
                "WhcNMjMwNDA1MTg1NzA0WjBXMQswCQYDVQQGEwJYWDEQMA4GA1UECAwHdW5rbm93" +
                "bjEQMA4GA1UEBwwHdW5rbm93bjEQMA4GA1UECgwHdW5rbm93bjESMBAGA1UEAwwJ" +
                "bG9jYWxob3N0MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAv0SlzA9w" +
                "rmEXLboFFn49lBMMbK1BJANSxno656VslrK7Mq4A9cF+qDjSOFDHNCgaHl/4oqIp" +
                "IKa2/RUqeXKs2qrSakZAOte6Cw4m3sLEIWDWdsoaCH4bRuOt6nQwDmPfTtLlJcU2" +
                "yBvOJoeaGfsKreaInKq6eR91qWqUbVBzSpxLIyolgp9vyurPvCPYreotsqWvQnUQ" +
                "cYtW9C2JJ+0Xwyb7Zon5JVBkwHIn7z4aISiyJuYcu1NmrmAjQLl8+SsUHhZ1iIrh" +
                "ekEFMinoQ+QdMA0mmSX8QszfQqAvYrsD652nZ9tCVs2xDt1rOLRL3JDu4K96kua4" +
                "ApEADoc2KYbErssbyXUPrVlJe/5XVnId99/l7RLLpnZV61iWr2JkZUHZaEF3fYs4" +
                "dKJdQELeB9biBgi/VkO81XPG+DzBxrEpRPbVeQF9jGEQ6CGNXss6q5qdxAPjmAsk" +
                "CMr2gKaQCz3JICPjR0Cz5NqbwVMyXnFbxi5oLpms44NCDWfXXGG7y16pDUqGQE/U" +
                "TvQD3y2z/Br4aUB8utGUwiL/m3W96L3JxK48BQlJ+eCuRFlWxcqmHKmdDu5wB9hQ" +
                "QnE4ihfh0pw/IeKodJD7b1Oj/L+MjtYM+e/+q8rMwEVEwnFpOqhtA1kVA5oLgdI8" +
                "+L0kMVi7G78qAGnMYh7q8f5OOUonXy71shMCAwEAAaNQME4wHQYDVR0OBBYEFBQo" +
                "yqB/pUPICm/mEGCYs3CNzLvNMB8GA1UdIwQYMBaAFBQoyqB/pUPICm/mEGCYs3CN" +
                "zLvNMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQELBQADggIBAKOPOY1i481G3ub2" +
                "BJiupKzUMxnvssWysx1k2TQSc73ApXef3hjFbllH2TD3kD+86BopUljxoI0fkoh8" +
                "PkKyaMX5N42Aq5GQQhUhuKBvvcgKRM6xqTQEZbjZMCb0zS1PEDPOlNbGhOhQPSSX" +
                "Q9z4uH6ISuPCmNUhp+plJe2BqV+gUpTKBMkCka+jjoJokcgRq7tMIRuN+fNA/Hn6" +
                "mAqMslb+CPrfWY0yarJod2RA9pc4JjZii7azjc5crttRwfHJGWRSZcLLYm+wkMCY" +
                "xeCoOosprrtAfKE3OzfM9lRLrg3C1YwVLWPJJleTVzv6qcZm3EI7PRARIfw1tGky" +
                "0YavsTIcmOjPO3rCw01FeIYyBnECxoX0UOapRaHbDQ/PJIFXV2x/hrLe2g/p/fJv" +
                "7OTUqVcV3rAkolmbryNQCOlXkojBTw7CcQLECaBRLPBZv+vezs9WE+AfvGN6RcPz" +
                "VIGNi7ItdiN6PPYDj2csiLRQtfpqzMmY3mER9M58JZknwreS0kNKx/Z2txYBjhtk" +
                "HHEuNaIkcb2ePNqkLj7UHPxjWb6nvx80t0bC0V9OllmkQ9jjdb1KGbpgepYeDDSg" +
                "3/1M+2uTu2oJhi+AWB86ozJpooMmMvyonZWJz2jRpCudWHcvy3lOPPIDjojebuHa" +
                "BQT6btPccxaCO2ysEg1k9VgNa4M0", certs.get(0));
    }

    @Test
    void multipleCertificateParse() throws IOException {
        String certContent = Files.readString(new File("target/test-classes/cert02.pem").toPath());
        List<String> certs = ClowderConfigSource.readCerts(certContent);

        assertNotNull(certs);
        assertEquals(3, certs.size());
    }
}
