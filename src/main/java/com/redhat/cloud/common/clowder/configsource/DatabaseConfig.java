package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
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
