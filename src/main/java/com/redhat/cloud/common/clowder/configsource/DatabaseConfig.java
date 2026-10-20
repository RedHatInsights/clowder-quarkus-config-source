package com.redhat.cloud.common.clowder.configsource;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class DatabaseConfig {

    public String adminPassword;
    public String adminUsername;
    public String hostname;
    public String name;
    public String password;
    public Integer port;
    public String sslMode;
    public String username;
    public String rdsCa;
}
