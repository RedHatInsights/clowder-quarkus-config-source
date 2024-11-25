package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClowderConfig {

    public DatabaseConfig database;
    public InMemoryDb inMemoryDb;
    public List<EndpointConfig> endpoints;
    public List<PrivateEndpointConfig> privateEndpoints;
    public KafkaConfig kafka;
    public LoggingConfig logging;
    public FeatureFlagsConfig featureFlags;
    public String metricsPath;
    public Integer metricsPort;
    public Integer privatePort;
    public Integer publicPort;
    public Integer webPort;
    public String tlsCAPath;
}
