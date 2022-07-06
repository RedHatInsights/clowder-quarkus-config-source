package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SaslConfig {

    public String username;
    public String password;
    public String saslMechanism;
    public String securityProtocol;
}
