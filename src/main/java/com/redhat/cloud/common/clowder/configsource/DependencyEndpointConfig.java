package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

/** Metadata for a single Clowder V2 dependency endpoint. */
@RegisterForReflection
public class DependencyEndpointConfig {

    public String uri;
    public Boolean authenticated;
    @JsonProperty("ca_certificate")
    public String caCertificate;
}
