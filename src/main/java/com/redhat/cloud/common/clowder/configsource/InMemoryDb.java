package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InMemoryDb {

    public String hostname;
    public Integer port;
    public String username;
    public String password;
}
