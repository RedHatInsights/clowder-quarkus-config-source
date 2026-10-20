package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.BrokerConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

import java.util.List;
import java.util.Optional;

import static com.redhat.cloud.common.clowder.configsource.utils.CertUtils.createTempCertFile;

public class KafkaSecurityClowderPropertyHandler extends ClowderPropertyHandler {

    public static final String KAFKA_SASL_JAAS_CONFIG_KEY = "kafka.sasl.jaas.config";
    public static final String KAFKA_SASL_MECHANISM_KEY = "kafka.sasl.mechanism";
    public static final String KAFKA_SECURITY_PROTOCOL_KEY = "kafka.security.protocol";
    public static final String KAFKA_SSL_TRUSTSTORE_LOCATION_KEY = "kafka.ssl.truststore.location";
    public static final String KAFKA_SSL_TRUSTSTORE_TYPE_KEY = "kafka.ssl.truststore.type";
    public static final String CAMEL_KAFKA_SASL_JAAS_CONFIG_KEY = "camel.component.kafka.sasl-jaas-config";
    public static final String CAMEL_KAFKA_SASL_MECHANISM_KEY = "camel.component.kafka.sasl-mechanism";
    public static final String CAMEL_KAFKA_SECURITY_PROTOCOL_KEY = "camel.component.kafka.security-protocol";
    public static final String CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY = "camel.component.kafka.ssl-truststore-location";
    public static final String CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY = "camel.component.kafka.ssl-truststore-type";
    public static final String KAFKA_SSL_TRUSTSTORE_TYPE_VALUE = "PEM";
    private static final List<String> KAFKA_SASL_KEYS = List.of(
            KAFKA_SASL_JAAS_CONFIG_KEY,
            KAFKA_SASL_MECHANISM_KEY,
            KAFKA_SECURITY_PROTOCOL_KEY,
            KAFKA_SSL_TRUSTSTORE_LOCATION_KEY,
            KAFKA_SSL_TRUSTSTORE_TYPE_KEY,
            CAMEL_KAFKA_SASL_JAAS_CONFIG_KEY,
            CAMEL_KAFKA_SASL_MECHANISM_KEY,
            CAMEL_KAFKA_SECURITY_PROTOCOL_KEY,
            CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY,
            CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY);

    private static final List<String> KAFKA_SSL_KEYS = List.of(
            KAFKA_SECURITY_PROTOCOL_KEY,
            KAFKA_SSL_TRUSTSTORE_LOCATION_KEY,
            KAFKA_SSL_TRUSTSTORE_TYPE_KEY,
            CAMEL_KAFKA_SECURITY_PROTOCOL_KEY,
            CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY,
            CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY);

    private final boolean expose;
    private final Optional<BrokerConfig> saslBroker;
    private final Optional<BrokerConfig> sslBroker;

    public KafkaSecurityClowderPropertyHandler(ClowderConfig clowderConfig, boolean expose) {
        super(clowderConfig);

        this.expose = expose;
        if (clowderConfig.kafka != null && clowderConfig.kafka.brokers != null) {
            this.saslBroker = clowderConfig.kafka.brokers.stream()
                    .filter(broker -> "sasl".equals(broker.authtype))
                    .findAny();
            this.sslBroker = clowderConfig.kafka.brokers.stream()
                    .filter(broker -> "SSL".equals(broker.securityProtocol))
                    .findAny();
        } else {
            this.saslBroker = Optional.empty();
            this.sslBroker = Optional.empty();
        }
    }

    @Override
    public List<String> provides() {
        if (expose) {
            if (sslBroker.isPresent()) {
                return KAFKA_SSL_KEYS;
            } else if (saslBroker.isPresent()) {
                return KAFKA_SASL_KEYS;
            }
        }

        return List.of();
    }

    @Override
    public boolean handles(String property) {
        return KAFKA_SSL_KEYS.contains(property) || KAFKA_SASL_KEYS.contains(property);
    }

    @Override
    public String handle(String property, ClowderConfigSource configSource) {
        if (clowderConfig.kafka == null) {
            throw new IllegalStateException("Kafka base object not present, can't set Kafka values");
        }

        if (saslBroker.isPresent()) {
            return handleKafkaSaslKey(property, configSource);
        } else if (sslBroker.isPresent()) {
            return handleKafkaSslKey(property, configSource);
        }

        return configSource.getExistingValue(property);
    }

    private String handleKafkaSaslKey(String property, ClowderConfigSource configSource) {
        switch (property) {
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
                throw new IllegalStateException(String.format("Config key: '%s' shouldn't be present for a Kafka SASL configuration, please check your config file", property));
        }
    }

    private String handleKafkaSslKey(String property, ClowderConfigSource configSource) {
        switch (property) {
            case KAFKA_SECURITY_PROTOCOL_KEY:
            case CAMEL_KAFKA_SECURITY_PROTOCOL_KEY:
                return sslBroker.get().securityProtocol;
            case KAFKA_SSL_TRUSTSTORE_LOCATION_KEY:
            case CAMEL_KAFKA_SSL_TRUSTSTORE_LOCATION_KEY:
                return createTempKafkaCertFile(sslBroker.get().cacert);
            case KAFKA_SSL_TRUSTSTORE_TYPE_KEY:
            case CAMEL_KAFKA_SSL_TRUSTSTORE_TYPE_KEY:
                if (configSource.getValue(KAFKA_SSL_TRUSTSTORE_LOCATION_KEY) != null) {
                    return KAFKA_SSL_TRUSTSTORE_TYPE_VALUE;
                }
            default:
                throw new IllegalStateException(String.format("Config key: '%s' shouldn't be present for a Kafka SSL configuration, please check your config file", property));
        }
    }

    private String createTempKafkaCertFile(String certData) {
        return certData != null ? createTempCertFile("kafka-cacert", certData) : null;
    }
}
