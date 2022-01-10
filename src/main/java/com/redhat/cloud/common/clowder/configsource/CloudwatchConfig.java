package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudwatchConfig {

    public String accessKeyId;
    public String logGroup;
    public String region;
    public String secretAccessKey;
}
