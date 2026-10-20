package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class BrokerConfig {

    public String hostname;
    public Integer port;
    public String cacert;
    public String authtype;
    public SaslConfig sasl;
    public String securityProtocol;
}
