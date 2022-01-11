package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LoggingConfig {

    public CloudwatchConfig cloudwatch;
    public String type;
}
