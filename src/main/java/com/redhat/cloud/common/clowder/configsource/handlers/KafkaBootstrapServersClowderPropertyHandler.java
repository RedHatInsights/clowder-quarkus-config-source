package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.BrokerConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;

public class KafkaBootstrapServersClowderPropertyHandler extends ClowderPropertyHandler {

    private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";
    private static final String CAMEL_KAFKA_BROKERS = "camel.component.kafka.brokers";

    public KafkaBootstrapServersClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public boolean handles(String property) {
        return property.equals(KAFKA_BOOTSTRAP_SERVERS) || property.equals(CAMEL_KAFKA_BROKERS);
    }

    @Override
    public String handle(String property, ClowderConfigSource configSource) {
        if (clowderConfig.kafka == null) {
            throw new IllegalStateException("Kafka base object not present, can't set Kafka values");
        } else {
            StringBuilder sb = new StringBuilder();
            for (BrokerConfig broker: clowderConfig.kafka.brokers) {
                if (!sb.isEmpty()) {
                    sb.append(',');
                }
                sb.append(broker.hostname).append(":").append(broker.port);
            }

            return sb.toString();
        }
    }
}
