package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A Config source that is using the ClowderAppConfig
 */
public class ClowderConfigSource implements ConfigSource {

    public static final String CLOWDER_CONFIG_SOURCE = "ClowderConfigSource";

    // Kafka SASL config keys.
    public static final String KAFKA_SASL_JAAS_CONFIG_KEY = "kafka.sasl.jaas.config";
    public static final String KAFKA_SASL_MECHANISM_KEY = "kafka.sasl.mechanism";
    public static final String KAFKA_SECURITY_PROTOCOL_KEY = "kafka.security.protocol";
    public static final String KAFKA_SSL_TRUSTSTORE_LOCATION_KEY = "kafka.ssl.truststore.location";
    public static final String KAFKA_SSL_TRUSTSTORE_TYPE_KEY = "kafka.ssl.truststore.type";

    // Kafka SASL config values.
    public static final String KAFKA_SSL_TRUSTSTORE_TYPE_VALUE = "PEM";

    private static final String QUARKUS_LOG_CLOUDWATCH = "quarkus.log.cloudwatch";
    private static final String QUARKUS_DATASOURCE_JDBC_URL = "quarkus.datasource.jdbc.url";
    private static final String CLOWDER_ENDPOINTS = "clowder.endpoints.";
    private static List<String> KAFKA_SASL_KEYS = List.of(
            KAFKA_SASL_JAAS_CONFIG_KEY,
            KAFKA_SASL_MECHANISM_KEY,
            KAFKA_SECURITY_PROTOCOL_KEY,
            KAFKA_SSL_TRUSTSTORE_LOCATION_KEY,
            KAFKA_SSL_TRUSTSTORE_TYPE_KEY
    );

    Logger log = Logger.getLogger(getClass().getName());
    private final Map<String, ConfigValue> existingValues;
    private ClowderConfig root;
    private boolean translate = true;

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
            if (configKey.equals("kafka.bootstrap.servers")) {
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
                            String username = saslBroker.get().sasl.username;
                            String password = saslBroker.get().sasl.password;
                            switch (saslBroker.get().sasl.saslMechanism) {
                                case "PLAIN":
                                    return "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"" + username + "\" password=\"" + password + "\";";
                                case "SCRAM-SHA-512":
                                    return "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"" + username + "\" password=\"" + password + "\";";
                            }
                        case KAFKA_SASL_MECHANISM_KEY:
                            return saslBroker.get().sasl.saslMechanism;
                        case KAFKA_SECURITY_PROTOCOL_KEY:
                            return saslBroker.get().sasl.securityProtocol;
                        case KAFKA_SSL_TRUSTSTORE_LOCATION_KEY:
                            return createTempKafkaCertFile(saslBroker.get().cacert);
                        case KAFKA_SSL_TRUSTSTORE_TYPE_KEY:
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
                            tracing = "tracing:";
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
                    String requestedEndpoint = configKey.substring(CLOWDER_ENDPOINTS.length());
                    for (EndpointConfig endpoint : root.endpoints) {
                        String currentEndpoint = endpoint.app + "-" + endpoint.name;
                        if (currentEndpoint.equals(requestedEndpoint)) {
                            return "http://" + endpoint.hostname + ":" + endpoint.port;
                        }
                    }
                    log.warn("Endpoint '" + requestedEndpoint + "' not found in the endpoints section");
                    return null;
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
            File certFile = File.createTempFile(fileName, ".crt");
            try {
                certFile.deleteOnExit();
            } catch (SecurityException e) {
                log.warnf(e, "Delete on exit of the '%s' cert file denied by the security manager", fileName);
            }
            return Files.write(Path.of(certFile.getAbsolutePath()), cert).toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Certificate file creation failed", e);
        }
    }
}
