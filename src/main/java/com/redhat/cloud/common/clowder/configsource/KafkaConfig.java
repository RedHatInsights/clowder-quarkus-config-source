package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class KafkaConfig {

    public List<BrokerConfig> brokers;
    public List<TopicConfig> topics;
}
