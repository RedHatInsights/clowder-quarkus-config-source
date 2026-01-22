package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class SaslConfig {

    public String username;
    public String password;
    public String saslMechanism;
    public String securityProtocol;
}
