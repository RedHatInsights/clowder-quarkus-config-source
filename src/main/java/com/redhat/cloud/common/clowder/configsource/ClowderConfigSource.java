package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A Config source that is using the ClowderAppConfig
 */
public class ClowderConfigSource implements ConfigSource {

    public static final String CLOWDER_CONFIG_SOURCE = "ClowderConfigSource";

    // Kafka config keys.
    public static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";
    public static final String KAFKA_SASL_JAAS_CONFIG_KEY = "kafka.sasl.jaas.config";
    public static final String KAFKA_SASL_MECHANISM_KEY = "kafka.sasl.mechanism";
    public static final String KAFKA_SECURITY_PROTOCOL_KEY = "kafka.security.protocol";
    public static final String KAFKA_SSL_TRUSTSTORE_LOCATION_KEY = "kafka.ssl.truststore.location";
    public static final String KAFKA_SSL_TRUSTSTORE_TYPE_KEY = "kafka.ssl.truststore.type";

    // Camel Kafka config keys.
    public static final String CAMEL_KAFKA_BROKERS = "camel.component.kafka.brokers";
    public static final String CAMEL_KAFKA_SASL_JAAS_CONFIG_KEY = "camel.component.kafka.sasl-jaas-config";
    public static final String CAMEL_KAFKA_SASL_MECHANISM_KEY = "camel.component.kafka.sasl-mechanism";
    public static final String CAMEL_KAFKA_SECURITY_PROTOCOL_KEY = "camel.component.kafka.security-protocol";
    public static final String CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY = "camel.component.kafka.ssl-truststore-location";
    public static final String CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY = "camel.component.kafka.ssl-truststore-type";

    // Kafka SASL config values.
    public static final String KAFKA_SSL_TRUSTSTORE_TYPE_VALUE = "PEM";

    private static final String QUARKUS_LOG_CLOUDWATCH = "quarkus.log.cloudwatch";
    private static final String QUARKUS_DATASOURCE_JDBC_URL = "quarkus.datasource.jdbc.url";
    private static final String CLOWDER_ENDPOINTS = "clowder.endpoints.";
    private static final String CLOWDER_PRIVATE_ENDPOINTS = "clowder.private-endpoints.";
    private static final String CLOWDER_ENDPOINTS_PARAM_URL = "url";
    private static final String CLOWDER_ENDPOINT_STORE_TYPE = "PKCS12";
    private static final String CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PATH = "trust-store-path";
    private static final String CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PASSWORD = "trust-store-password";
    private static final String CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_TYPE = "trust-store-type";
    private static List<String> KAFKA_SASL_KEYS = List.of(
            KAFKA_SASL_JAAS_CONFIG_KEY,
            KAFKA_SASL_MECHANISM_KEY,
            KAFKA_SECURITY_PROTOCOL_KEY,
            KAFKA_SSL_TRUSTSTORE_LOCATION_KEY,
            KAFKA_SSL_TRUSTSTORE_TYPE_KEY,
            CAMEL_KAFKA_SASL_JAAS_CONFIG_KEY,
            CAMEL_KAFKA_SASL_MECHANISM_KEY,
            CAMEL_KAFKA_SECURITY_PROTOCOL_KEY,
            CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY,
            CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY
    );
    // Fixed length for the password used - it could probably be anything else - but ran my tests on a FIPS environment with these.
    private static final int DEFAULT_PASSWORD_LENGTH = 33;
    private static final Integer PORT_NOT_SET = 0;

    Logger log = Logger.getLogger(getClass().getName());
    private final Map<String, ConfigValue> existingValues;
    private ClowderConfig root;
    private boolean translate = true;

    private String trustStorePath;
    private String trustStorePassword;

    /**
     * <p>Constructor for ClowderConfigSource.</p>
     *
     * @param configFile Name/Path of a file to read the config from.
     * @param exProp {@link java.util.Map} containing the existing properties from e.g. application.properties.
     */
    public ClowderConfigSource(String configFile, Map<String, ConfigValue> exProp) {

        existingValues = exProp;
        File file = new File(configFile);
        if (!file.canRead()) {
            log.warn("Can't read clowder config from " + file.getAbsolutePath() + ", not doing translations.");
            translate = false;
        } else {
            try {
                String configJson = Files.readString(file.toPath());
                root = new ObjectMapper().readValue(configJson, ClowderConfig.class);
            } catch (IOException e) {
                log.warn("Reading the clowder config failed, not doing translations", e);
                translate = false;
            }
        }
    }

    @Override
    public Map<String, String> getProperties() {

        Map<String,String> props = new HashMap<>();
        Set<Map.Entry<String, ConfigValue>> entries = existingValues.entrySet();
        for (Map.Entry<String,ConfigValue> entry : entries) {
            String newVal = getValue(entry.getKey());
            if (newVal == null) {
                newVal = entry.getValue().getValue();
            }
            props.put(entry.getKey(),newVal);
        }

        return props;
    }

    @Override
    public Set<String> getPropertyNames() {
        return existingValues.keySet();
    }

    @Override
    public int getOrdinal() {
        // Provide a value higher than 250 to it overrides application.properties
        return 270;
    }

    /**
     * Return a value for a config property.
     * We need to look at the clowder provided data and eventually replace
     * the requested values from application.properties with what clowder
     * provides us, which may be different.
     *
     * If the configfile was bad, we return the existing values.
     */
    @Override
    public String getValue(String configKey) {

        // This matches against the property as in application.properties
        // For profiles != prod, values are requested first like
        // %<profile>.property. E.g. %dev.quarkus.http.port

        if (translate) {

            if (configKey.equals("quarkus.http.port")) {
                return String.valueOf(root.webPort);
            }
            if (configKey.equals(KAFKA_BOOTSTRAP_SERVERS) || configKey.equals(CAMEL_KAFKA_BROKERS)) {
                if (root.kafka == null) {
                    throw new IllegalStateException("Kafka base object not present, can't set Kafka values");
                }
                StringBuilder sb = new StringBuilder();
                for (BrokerConfig broker: root.kafka.brokers) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append(broker.hostname + ":" + broker.port);
                }
                return sb.toString();
            }

            if (configKey.startsWith("mp.messaging") && configKey.endsWith(".topic")) {
                if (root.kafka == null) {
                    throw new IllegalStateException("Kafka base object not present, can't set Kafka values");
                }
                // We need to find the replaced topic by first finding
                // the requested name and then getting the replaced name
                String requested = existingValues.get(configKey).getValue();
                for (TopicConfig topic : root.kafka.topics) {
                    if (topic.requestedName.equals(requested)) {
                        return topic.name;
                    }
                }
                return requested;
            }

            if (KAFKA_SASL_KEYS.contains(configKey)) {
                if (root.kafka == null) {
                    throw new IllegalStateException("Kafka base object not present, can't set Kafka values");
                }
                Optional<BrokerConfig> saslBroker = root.kafka.brokers.stream()
                        .filter(broker -> "sasl".equals(broker.authtype))
                        .findAny();
                if (saslBroker.isPresent()) {
                    switch (configKey) {
                        case KAFKA_SASL_JAAS_CONFIG_KEY:
                        case CAMEL_KAFKA_SASL_JAAS_CONFIG_KEY:
                            String username = saslBroker.get().sasl.username;
                            String password = saslBroker.get().sasl.password;
                            switch (saslBroker.get().sasl.saslMechanism) {
                                case "PLAIN":
                                    return "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + username + "\" password=\"" + password + "\";";
                                case "SCRAM-SHA-512":
                                    return "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"" + username + "\" password=\"" + password + "\";";
                            }
                        case KAFKA_SASL_MECHANISM_KEY:
                        case CAMEL_KAFKA_SASL_MECHANISM_KEY:
                            return saslBroker.get().sasl.saslMechanism;
                        case KAFKA_SECURITY_PROTOCOL_KEY:
                        case CAMEL_KAFKA_SECURITY_PROTOCOL_KEY:
                            return saslBroker.get().sasl.securityProtocol;
                        case KAFKA_SSL_TRUSTSTORE_LOCATION_KEY:
                        case CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY:
                            return createTempKafkaCertFile(saslBroker.get().cacert);
                        case KAFKA_SSL_TRUSTSTORE_TYPE_KEY:
                        case CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY:
                            return KAFKA_SSL_TRUSTSTORE_TYPE_VALUE;
                        default:
                            throw new IllegalStateException("Unexpected Kafka SASL config key: " + configKey);
                    }
                }
            }

            if (configKey.startsWith("quarkus.datasource")) {
                String item = configKey.substring("quarkus.datasource.".length());
                if (root.database == null) {
                    throw new IllegalStateException("No database section found");
                }
                if (item.equals("username")) {
                    return root.database.username;
                }
                String sslMode = root.database.sslMode;
                boolean useSsl = !sslMode.equals("disable");
                boolean verifyFull = sslMode.equals("verify-full");

                if (item.equals("password")) {
                    return root.database.password;
                }
                if (item.equals("jdbc.url")) {
                    String hostPortDb = getHostPortDb(root.database);
                    String tracing = "";
                    if (existingValues.containsKey(QUARKUS_DATASOURCE_JDBC_URL)) {
                        String url = existingValues.get(QUARKUS_DATASOURCE_JDBC_URL).getValue();
                        if (url.contains(":tracing:")) {
                            // TODO Remove this block (tracing) later.
                            log.warn("The support of OpenTracing in this library is deprecated and will be removed soon. Please consider switching to OpenTelemetry.");
                            tracing = "tracing:";
                        } else if (url.contains(":otel:")) {
                            /*
                             * The existing JDBC URL is the one coming from the application.properties file.
                             * If that URL contains "otel" then it means that the app is able to connect to
                             * the database through the OpenTelemetry JDBC layer.
                             */
                            tracing = "otel:";
                        }
                    }
                    String jdbcUrl = String.format("jdbc:%s%s", tracing, hostPortDb);
                    if (useSsl) {
                        jdbcUrl = jdbcUrl + "?sslmode=" + sslMode;
                    }
                    if (verifyFull) {
                        jdbcUrl = jdbcUrl + "&sslrootcert=" + createTempRdsCertFile(root.database.rdsCa);
                    }
                    return jdbcUrl;
                }
                if (item.startsWith("reactive.")) {
                    if (item.equals("reactive.url")) {
                        return getHostPortDb(root.database);
                    }
                    if (item.equals("reactive.postgresql.ssl-mode")) {
                        return sslMode;
                    }
                    if (verifyFull) {
                        if (item.equals("reactive.hostname-verification-algorithm")) {
                            return "HTTPS";
                        }
                        if (item.equals("reactive.trust-certificate-pem")) {
                            return "true";
                        }
                        if (item.equals("reactive.trust-certificate-pem.certs")) {
                            return createTempRdsCertFile(root.database.rdsCa);
                        }
                    }
                }
            }

            if (configKey.startsWith(QUARKUS_LOG_CLOUDWATCH)) {
                if (root.logging == null) {
                    throw new IllegalStateException("No logging section found");
                }
                if (root.logging.cloudwatch == null) {
                    throw new IllegalStateException("No cloudwatch section found in logging object");
                }

                // Check for not null type and not "null" provider to enable and read
                // cloudwatch properties from Clowder config.
                // Note that the "null" type is a Clowder logging provider that disables
                // central logging.
                // Empty string type has to be treated as cloudwatch, as one of the Clowder
                // logging providers (appinterface) did not set it correctly.
                if (root.logging.type != null && !root.logging.type.equals("null")) {
                    int prefixLen = QUARKUS_LOG_CLOUDWATCH.length();
                    String sub = configKey.substring(prefixLen+1);
                    switch (sub) {
                        case "access-key-id":
                            return root.logging.cloudwatch.accessKeyId;
                        case "access-key-secret":
                            return root.logging.cloudwatch.secretAccessKey;
                        case "region":
                            return root.logging.cloudwatch.region;
                        case "log-group":
                            return root.logging.cloudwatch.logGroup;
                        default:
                            // fall through to fetching the value from application.properties
                    }
                } else {
                    // treat null logging type as disabled CloudWatch logging
                    if (configKey.equals(QUARKUS_LOG_CLOUDWATCH + ".enabled")) {
                        return "false";
                    }
                }
            }

            if (configKey.startsWith(CLOWDER_ENDPOINTS)) {
                try {
                    if (root.endpoints == null) {
                        throw new IllegalStateException("No endpoints section found");
                    }
                    String requestedEndpointConfig = configKey.substring(CLOWDER_ENDPOINTS.length());
                    String[] configPath = requestedEndpointConfig.split("\\.");

                    String requestedEndpoint;
                    String param;
                    final String FORMAT_EXAMPLE = "[endpoint-name].[url|trust-store-path|trust-store-password|trust-store-type]";
                    if (configPath.length == 1) {
                        log.warn("Endpoint '" + requestedEndpointConfig + "' is using the old format. Please move to the new one: " + FORMAT_EXAMPLE);
                        requestedEndpoint = configPath[0];
                        param = CLOWDER_ENDPOINTS_PARAM_URL;
                    } else if (configPath.length != 2) {
                        throw new IllegalArgumentException("Endpoint '" + requestedEndpointConfig + "' expects a different format: " + FORMAT_EXAMPLE);
                    } else {
                        requestedEndpoint = configPath[0];
                        param = configPath[1];
                    }

                    EndpointConfig endpoint = null;

                    for (EndpointConfig endpointCandidate : root.endpoints) {
                        String currentEndpoint = endpointCandidate.app + "-" + endpointCandidate.name;
                        if (currentEndpoint.equals(requestedEndpoint)) {
                            endpoint = endpointCandidate;
                            break;
                        }
                    }

                    if (endpoint == null) {
                        log.warn("Endpoint '" + requestedEndpoint + "' not found in the endpoints section");
                        return null;
                    }

                    switch (param) {
                        case CLOWDER_ENDPOINTS_PARAM_URL:
                            if (usesTls(endpoint)) {
                                return "https://" + endpoint.hostname + ":" + endpoint.tlsPort;
                            } else {
                                return "http://" + endpoint.hostname + ":" + endpoint.port;
                            }
                        case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PATH:
                            if (usesTls(endpoint)) {
                                ensureTlsCertPathIsPresent();

                                createTruststoreFile(root.tlsCAPath);
                                return trustStorePath;
                            }

                            return null;
                        case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PASSWORD:
                            if (usesTls(endpoint)) {
                                ensureTlsCertPathIsPresent();

                                createTruststoreFile(root.tlsCAPath);
                                return trustStorePassword;
                            }

                            return null;
                        case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_TYPE:
                            if (usesTls(endpoint)) {
                                ensureTlsCertPathIsPresent();

                                return CLOWDER_ENDPOINT_STORE_TYPE;
                            }

                            return null;
                        default:
                            log.warn("Endpoint '" + requestedEndpoint + "' requested an unknown param: '" + param + "'");
                            return null;
                    }
                } catch (IllegalStateException e) {
                    log.errorf("Failed to load config key '%s' from the Clowder configuration: %s", configKey, e.getMessage());
                    throw e;
                }
            }

            if (configKey.startsWith(CLOWDER_PRIVATE_ENDPOINTS)) {
                try {
                    if (root.privateEndpoints == null) {
                        throw new IllegalStateException("No private endpoints section found");
                    }
                    final String requestedEndpointConfig = configKey.substring(CLOWDER_PRIVATE_ENDPOINTS.length());
                    final String[] configPath = requestedEndpointConfig.split("\\.");

                    final String requestedPrivateEndpoint;
                    final String param;
                    final String FORMAT_EXAMPLE = "[private-endpoint-name].[url|trust-store-path|trust-store-password|trust-store-type]";
                    if (configPath.length == 1) {
                        log.warnf("Private endpoint '%s' is using the old format. Please move to the new one: %s", requestedEndpointConfig, FORMAT_EXAMPLE);
                        requestedPrivateEndpoint = configPath[0];
                        param = CLOWDER_ENDPOINTS_PARAM_URL;
                    } else if (configPath.length != 2) {
                        throw new IllegalArgumentException(String.format("Private endpoint '%s' expects a different format: %s" +requestedEndpointConfig, FORMAT_EXAMPLE));
                    } else {
                        requestedPrivateEndpoint = configPath[0];
                        param = configPath[1];
                    }

                    PrivateEndpointConfig privateEndpoint = null;

                    for (final PrivateEndpointConfig privateEndpointCandidate : root.privateEndpoints) {
                        final String currentEndpoint = String.format("%s-%s", privateEndpointCandidate.app, privateEndpointCandidate.name);
                        if (currentEndpoint.equals(requestedPrivateEndpoint)) {
                            privateEndpoint = privateEndpointCandidate;
                            break;
                        }
                    }

                    if (privateEndpoint == null) {
                        log.warnf("Private endpoint '%s' not found in the private endpoints section", requestedPrivateEndpoint);
                        return null;
                    }

                    switch (param) {
                        case CLOWDER_ENDPOINTS_PARAM_URL:
                            if (usesTls(privateEndpoint)) {
                                return String.format("https://%s:%s", privateEndpoint.hostname, privateEndpoint.tlsPort);
                            } else {
                                return String.format("http://%s:%s", privateEndpoint.hostname, privateEndpoint.port);
                            }
                        case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PATH:
                            if (usesTls(privateEndpoint)) {
                                ensureTlsCertPathIsPresent();

                                createTruststoreFile(root.tlsCAPath);
                                return trustStorePath;
                            }

                            return null;
                        case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PASSWORD:
                            if (usesTls(privateEndpoint)) {
                                ensureTlsCertPathIsPresent();

                                createTruststoreFile(root.tlsCAPath);
                                return trustStorePassword;
                            }

                            return null;
                        case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_TYPE:
                            if (usesTls(privateEndpoint)) {
                                ensureTlsCertPathIsPresent();

                                return CLOWDER_ENDPOINT_STORE_TYPE;
                            }

                            return null;
                        default:
                            log.warnf("Private endpoint '%s' requested an unknown param: '%s'", requestedPrivateEndpoint, param);
                            return null;
                    }
                } catch (IllegalStateException e) {
                    log.errorf("Failed to load config key '%s' from the Clowder configuration: %s", configKey, e.getMessage());
                    throw e;
                }
            }
        }

        if (existingValues.containsKey(configKey)) {
            return existingValues.get(configKey).getValue();
        }
        else {
            return null;
        }
    }

    @Override
    public String getName() {
        return CLOWDER_CONFIG_SOURCE;
    }

    private String getHostPortDb(DatabaseConfig database) {
        return String.format("postgresql://%s:%d/%s",
                database.hostname,
                database.port,
                database.name);
    }

    private boolean usesTls(EndpointConfig endpointConfig) {
        return endpointConfig.tlsPort != null && !endpointConfig.tlsPort.equals(PORT_NOT_SET);
    }

    private boolean usesTls(final PrivateEndpointConfig privateEndpointConfig) {
        return privateEndpointConfig.tlsPort != null && !privateEndpointConfig.tlsPort.equals(PORT_NOT_SET);
    }

    private void ensureTlsCertPathIsPresent() {
        if (root.tlsCAPath == null || root.tlsCAPath.isBlank()) {
            throw new IllegalStateException("Requested tls port for endpoint but did not provide tlsCAPath");
        }
    }

    private void createTruststoreFile(String certPath) {
        if (this.trustStorePath != null) {
            return;
        }

        try {
            String certContent = Files.readString(new File(certPath).toPath(), UTF_8);
            List<String> base64Certs = readCerts(certContent);

            List<X509Certificate> certificates = parsePemCert(base64Certs)
                    .stream()
                    .map(this::buildX509Cert)
                    .collect(Collectors.toList());

            if (certificates.size() < 1) {
                throw new IllegalStateException("Could not parse any certificate in the file");
            }

            // Generate a keystore by hand
            // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html

            KeyStore truststore = KeyStore.getInstance(CLOWDER_ENDPOINT_STORE_TYPE);

            // Per the docs, we need to init the new keystore with load(null)
            truststore.load(null);

            for (int i = 0; i < certificates.size(); i++) {
                truststore.setCertificateEntry("cert-" + i, certificates.get(i));
            }

            char[] password = buildPassword(base64Certs.get(0));
            this.trustStorePath = writeTruststore(truststore, password);
            this.trustStorePassword = new String(password);
        } catch (IOException ioe) {
            throw new IllegalStateException("Couldn't load the certificate, but we were requested a truststore", ioe);
        } catch (KeyStoreException kse) {
            throw new IllegalStateException("Couldn't load the keystore format PKCS12", kse);
        } catch (NoSuchAlgorithmException | CertificateException ce) {
            throw new IllegalStateException("Couldn't configure the keystore", ce);
        }
    }

    static List<String> readCerts(String certString) {
        return Arrays.stream(certString.split("-----BEGIN CERTIFICATE-----"))
                .filter(s -> !s.isEmpty())
                .map(s -> Arrays.stream(s
                                .split("-----END CERTIFICATE-----"))
                        .filter(s2 -> !s2.isEmpty())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Invalid certificate found"))

                )
                .map(String::trim)
                .map(s -> s.replaceAll("\n", ""))
                .collect(Collectors.toList());
    }

    private List<byte[]> parsePemCert(List<String> base64Certs) {
        return base64Certs
                .stream()
                .map(cert -> Base64.getDecoder().decode(cert.getBytes(StandardCharsets.UTF_8)))
                .collect(Collectors.toList());
    }

    private X509Certificate buildX509Cert(byte[] cert) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(cert));
        } catch (CertificateException certificateException) {
            throw new IllegalStateException("Couldn't load the x509 certificate factory", certificateException);
        }
    }

    private String writeTruststore(KeyStore keyStore, char[] password) {
        try {
            File file = createTempFile("truststore", ".trust");
            keyStore.store(new FileOutputStream(file), password);
            return file.getAbsolutePath();
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Truststore creation failed", e);
        }
    }

    private char[] buildPassword(String seed) {
        // To avoid having a fixed password - fetch the first characters from a string (the certificate)
        int size = Math.min(DEFAULT_PASSWORD_LENGTH, seed.length());
        char[] password = new char[size];
        seed.getChars(0, size, password, 0);
        return password;
    }

    private String createTempRdsCertFile(String certData) {
        if (certData != null) {
            return createTempCertFile("rds-ca-root", certData);
        } else {
            throw new IllegalStateException("'database.sslMode' is set to 'verify-full' in the Clowder config but the 'database.rdsCa' field is missing");
        }
    }

    private String createTempKafkaCertFile(String certData) {
        if (certData != null) {
            return createTempCertFile("kafka-cacert", certData);
        } else {
            return null;
        }
    }

    private String createTempCertFile(String fileName, String certData) {
        byte[] cert = certData.getBytes(UTF_8);
        try {
            File certFile = createTempFile(fileName, ".crt");
            return Files.write(Path.of(certFile.getAbsolutePath()), cert).toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Certificate file creation failed", e);
        }
    }

    private File createTempFile(String fileName, String suffix) throws IOException {
        File file = File.createTempFile(fileName, suffix);
        try {
            file.deleteOnExit();
        } catch (SecurityException e) {
            log.warnf(e, "Delete on exit of the '%s' cert file denied by the security manager", fileName);
        }
        return file;
    }
}
