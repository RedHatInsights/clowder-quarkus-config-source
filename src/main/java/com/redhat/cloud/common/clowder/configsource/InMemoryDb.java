package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class InMemoryDb {

    public String hostname;
    public Integer port;
    public String username;
    public String password;
}
