package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;
import com.redhat.cloud.common.clowder.configsource.DependencyEndpointConfig;
import com.redhat.cloud.common.clowder.configsource.DependencyEndpointsConfig;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in Clowder V2 dependency-endpoint lookup. Existing {@code clowder.*endpoints} properties
 * remain V1-only. Applications choose V1 vs V2 and own runtime fallback.
 */
public class DependencyEndpointsClowderPropertyHandler extends ClowderPropertyHandler {

    private static final String CLOWDER_DEPENDENCY_ENDPOINTS = "clowder.dependency-endpoints.";
    private static final Set<String> PARAMETERS = Set.of("uri", "authenticated", "ca-certificate",
            "trust-store-path", "trust-store-password", "trust-store-type");

    public DependencyEndpointsClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public boolean handles(String property) {
        return property.startsWith(getPropertyEndpointKey());
    }

    @Override
    public String handle(String property, ClowderConfigSource source) {
        return processDependencyEndpoints(property, source, clowderConfig.dependencyEndpoints);
    }

    protected String getPropertyEndpointKey() {
        return CLOWDER_DEPENDENCY_ENDPOINTS;
    }

    /**
     * Resolves a V2 dependency-endpoint property. Missing V2 returns null so property-expression
     * defaults and parallel V1 keys work. Applications own V1 vs V2 selection and runtime fallback.
     */
    protected String processDependencyEndpoints(String property, ClowderConfigSource source,
            DependencyEndpointsConfig versioned) {
        String prefix = getPropertyEndpointKey();
        String[] path = property.substring(prefix.length()).split("\\.", -1);
        if (path.length != 3 || path[0].isBlank() || path[1].isBlank() || path[2].isBlank()) {
            throw new IllegalArgumentException("Expected " + prefix + "<app>.<deployment>.<parameter>");
        }
        if (!PARAMETERS.contains(path[2])) {
            source.getLogger().warnf("Dependency endpoint requested an unknown parameter: '%s'", property);
            return null;
        }

        Map<String, Map<String, DependencyEndpointConfig>> v2 = versioned == null ? null : versioned.v2;
        if (v2 == null) {
            return null;
        }

        ResolvedEndpoint endpoint = resolve(v2, path[0], path[1]);
        if (endpoint == null) {
            return null;
        }

        return switch (path[2]) {
            case "uri" -> endpoint.uri();
            case "authenticated" -> Boolean.toString(endpoint.authenticated());
            case "ca-certificate" -> endpoint.caCertificate();
            case "trust-store-path" -> endpoint.caCertificate() == null ? null
                    : source.getTrustStorePath(endpoint.caCertificate());
            case "trust-store-password" -> endpoint.caCertificate() == null ? null
                    : source.getTrustStorePassword(endpoint.caCertificate());
            case "trust-store-type" -> endpoint.caCertificate() == null ? null : source.getTrustStoreType();
            default -> null;
        };
    }

    private ResolvedEndpoint resolve(Map<String, Map<String, DependencyEndpointConfig>> v2,
            String app, String deployment) {
        Map<String, DependencyEndpointConfig> deployments = v2.get(app);
        DependencyEndpointConfig endpoint = deployments == null ? null : deployments.get(deployment);
        if (endpoint == null || endpoint.uri == null || endpoint.uri.isBlank()) {
            return null;
        }
        validate(endpoint, app, deployment);
        String ca = endpoint.caCertificate == null || endpoint.caCertificate.isBlank()
                ? null : endpoint.caCertificate;
        return new ResolvedEndpoint(endpoint.uri, endpoint.authenticated, ca);
    }

    private void validate(DependencyEndpointConfig endpoint, String app, String deployment) {
        URI uri;
        try {
            uri = URI.create(endpoint.uri);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid V2 URI for " + app + "/" + deployment, e);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalStateException("Expected an HTTP(S) V2 URI without credentials or fragment for "
                    + app + "/" + deployment);
        }
        if (endpoint.authenticated == null) {
            throw new IllegalStateException("Missing V2 authenticated flag for " + app + "/" + deployment);
        }
    }

    private record ResolvedEndpoint(String uri, boolean authenticated, String caCertificate) { }
}
