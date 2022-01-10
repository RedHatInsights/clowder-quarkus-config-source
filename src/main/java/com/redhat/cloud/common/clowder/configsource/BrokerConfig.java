package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BrokerConfig {

    public String hostname;
    public Integer port;
    public String cacert;
    public String authtype;
    public SaslConfig sasl;
}
