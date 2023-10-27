package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.config.ConfigValue;
import java.util.HashSet;
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

import static com.redhat.cloud.common.clowder.configsource.utils.ComputedPropertiesUtils.PROPERTY_END;
import static com.redhat.cloud.common.clowder.configsource.utils.ComputedPropertiesUtils.PROPERTY_START;
import static com.redhat.cloud.common.clowder.configsource.utils.ComputedPropertiesUtils.getPropertyFromSystem;
import static com.redhat.cloud.common.clowder.configsource.utils.ComputedPropertiesUtils.getComputedProperties;
import static com.redhat.cloud.common.clowder.configsource.utils.ComputedPropertiesUtils.hasComputedProperties;
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
    private static final String CLOWDER_OPTIONAL_ENDPOINTS = "clowder.optional-endpoints.";
    private static final String CLOWDER_OPTIONAL_PRIVATE_ENDPOINTS = "clowder.optional-private-endpoints.";
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
    private static final String PROPERTY_DEFAULT = ":";

    Logger log = Logger.getLogger(getClass().getName());
    private final Map<String, ConfigValue> existingValues;
    private ClowderConfig root;
    private boolean translate = true;

    private String trustStorePath;
    private String trustStorePassword;

    private boolean exposeKafkaSslConfigKeys;

    /**
     * <p>Constructor for ClowderConfigSource.</p>
     *
     * @param configFile               Name/Path of a file to read the config from.
     * @param exProp                   {@link Map} containing the existing properties from e.g. application.properties.
     * @param exposeKafkaSslConfigKeys
     */
    public ClowderConfigSource(String configFile, Map<String, ConfigValue> exProp, boolean exposeKafkaSslConfigKeys) {

        existingValues = exProp;
        this.exposeKafkaSslConfigKeys = exposeKafkaSslConfigKeys;
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

        Set<String> availableProperties = new HashSet<>(existingValues.keySet());

        if (exposeKafkaSslConfigKeys) {
            for (String key : KAFKA_SASL_KEYS) {
                String value = getValue(key);
                if (value != null && !value.isBlank()) {
                    availableProperties.add(key);
                }
            }
        }
        return availableProperties;
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
                String requested = resolveValue(existingValues.get(configKey).getValue());
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

                    return this.processEndpoints(this.root.endpoints, configKey, CLOWDER_ENDPOINTS, "Endpoint");
                } catch (IllegalStateException e) {
                    log.errorf("Failed to load config key '%s' from the Clowder configuration: %s", configKey, e.getMessage());
                    throw e;
                }
            }

            if (configKey.startsWith(CLOWDER_OPTIONAL_ENDPOINTS)) {
                try {
                    if (root.endpoints == null) {
                        log.infof("No endpoints section found. Returning empty string for the \"%s\" configuration key", configKey);
                        return "";
                    }

                    return this.processEndpoints(this.root.endpoints, configKey, CLOWDER_OPTIONAL_ENDPOINTS, "Endpoint");
                } catch (final IllegalStateException e) {
                    log.errorf("Failed to load config key '%s' from the Clowder configuration: %s", configKey, e.getMessage());
                    throw e;
                }
            }

            if (configKey.startsWith(CLOWDER_OPTIONAL_PRIVATE_ENDPOINTS)) {
                try {
                    if (root.privateEndpoints == null) {
                        log.infof("No private endpoints section found. Returning empty string for the \"%s\" configuration key", configKey);
                        return "";
                    }

                    return this.processEndpoints(this.root.privateEndpoints, configKey, CLOWDER_OPTIONAL_PRIVATE_ENDPOINTS, "Private endpoint");
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

                    return this.processEndpoints(this.root.privateEndpoints, configKey, CLOWDER_PRIVATE_ENDPOINTS, "Private endpoint");
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

    /**
     * Attempts to find the corresponding value for the provided configuration
     * key. If the URL parameter has been requested, then depending on whether
     * the endpoint has a TLS port or not, a full URL including the protocol
     * is returned. For the rest of parameters it returns the corresponding
     * value.
     * @param endpointConfigs the list of configurations to be looked for the
     *                        corresponding value.
     * @param configKey       the configuration key specified in the
     *                        "application.properties" file.
     * @param clowderKey      the Clowder key which represents the path to
     *                        either the regular endpoints or the private
     *                        endpoints.
     * @param endpointType    the type of the endpoint which the function will
     *                        process. Used mainly for error and log messages.
     * @return a full URL including the protocol in the case of requesting an
     * endpoint's URL parameter. Regular values for the rest of the parameters.
     */
    private String processEndpoints(final List<? extends EndpointConfig> endpointConfigs, final String configKey, final String clowderKey, final String endpointType) {
        final String requestedEndpointConfig = configKey.substring(clowderKey.length());
        final String[] configPath = requestedEndpointConfig.split("\\.");

        final String requestedEndpoint;
        final String param;
        final String FORMAT_EXAMPLE = String.format("[%s].[url|trust-store-path|trust-store-password|trust-store-type]", endpointType);
        if (configPath.length == 1) {
            log.warnf("%s '%s' is using the old format. Please move to the new one: %s", endpointType, requestedEndpointConfig, FORMAT_EXAMPLE);
            requestedEndpoint = configPath[0];
            param = CLOWDER_ENDPOINTS_PARAM_URL;
        } else if (configPath.length != 2) {
            throw new IllegalArgumentException(String.format("%s '%s' expects a different format: %s", endpointType, requestedEndpointConfig, FORMAT_EXAMPLE));
        } else {
            requestedEndpoint = configPath[0];
            param = configPath[1];
        }

        EndpointConfig endpointConfig = null;

        for (final EndpointConfig configCandidate : endpointConfigs) {
            final String currentEndpoint = String.format("%s-%s", configCandidate.app, configCandidate.name);
            if (currentEndpoint.equals(requestedEndpoint)) {
                endpointConfig = configCandidate;
                break;
            }
        }

        if (endpointConfig == null) {
            log.warnf("%s '%s' not found in the %s section", endpointType, requestedEndpoint, clowderKey.substring(0, clowderKey.length() - 1));
            return null;
        }

        switch (param) {
            case CLOWDER_ENDPOINTS_PARAM_URL:
                if (usesTls(endpointConfig)) {
                    return String.format("https://%s:%s", endpointConfig.hostname, endpointConfig.tlsPort);
                } else {
                    return String.format("http://%s:%s", endpointConfig.hostname, endpointConfig.port);
                }
            case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PATH:
                if (usesTls(endpointConfig)) {
                    ensureTlsCertPathIsPresent();

                    createTruststoreFile(this.root.tlsCAPath);
                    return this.trustStorePath;
                }

                return null;
            case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PASSWORD:
                if (usesTls(endpointConfig)) {
                    ensureTlsCertPathIsPresent();

                    createTruststoreFile(this.root.tlsCAPath);
                    return this.trustStorePassword;
                }

                return null;
            case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_TYPE:
                if (usesTls(endpointConfig)) {
                    ensureTlsCertPathIsPresent();

                    return CLOWDER_ENDPOINT_STORE_TYPE;
                }

                return null;
            default:
                log.warnf("%s '%s' requested an unknown param: '%s'", endpointType, requestedEndpoint, param);
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

    private String resolveValue(String property) {
        if (!hasComputedProperties(property)) {
            return property;
        }

        for (String rawSystemProperty : getComputedProperties(property)) {
            String value = null;
            String systemProperty = rawSystemProperty;
            if (systemProperty.contains(PROPERTY_DEFAULT)) {
                int splitPosition = systemProperty.indexOf(PROPERTY_DEFAULT);
                value = systemProperty.substring(splitPosition + PROPERTY_DEFAULT.length());
                systemProperty = systemProperty.substring(0, splitPosition);

                if (hasComputedProperties(value)) {
                    value = resolveValue(value);
                }
            }

            ConfigValue computedValue = existingValues.get(systemProperty);
            if (computedValue != null) {
                value = computedValue.getValue();
            } else {
                // Check whether the system property is provided:
                value = getPropertyFromSystem(systemProperty, value);
            }

            property = property.replace(PROPERTY_START + rawSystemProperty + PROPERTY_END, value);
        }

        return property;
    }
}
