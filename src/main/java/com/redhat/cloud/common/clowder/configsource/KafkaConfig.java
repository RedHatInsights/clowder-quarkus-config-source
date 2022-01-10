package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaConfig {

    public List<BrokerConfig> brokers;
    public List<TopicConfig> topics;
}
