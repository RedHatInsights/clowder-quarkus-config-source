package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;
import com.redhat.cloud.common.clowder.configsource.DependencyEndpointConfig;
import com.redhat.cloud.common.clowder.configsource.DependencyEndpointsConfig;
import com.redhat.cloud.common.clowder.configsource.EndpointConfig;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Opt-in V2-first lookup. Existing clowder.*endpoints properties remain V1-only. */
public class DependencyEndpointsClowderPropertyHandler extends ClowderPropertyHandler {

    private static final Set<String> PARAMETERS = Set.of("uri", "authenticated", "ca-certificate",
            "trust-store-path", "trust-store-password", "trust-store-type");

    private final boolean privateEndpoints;
    private final boolean optional;
    private final String prefix;

    public DependencyEndpointsClowderPropertyHandler(ClowderConfig root, boolean privateEndpoints, boolean optional) {
        super(root);
        this.privateEndpoints = privateEndpoints;
        this.optional = optional;
        this.prefix = "clowder." + (optional ? "optional-" : "")
                + (privateEndpoints ? "private-" : "") + "dependency-endpoints.";
    }

    @Override
    public boolean handles(String property) {
        return property.startsWith(prefix);
    }

    @Override
    public String handle(String property, ClowderConfigSource source) {
        String[] path = property.substring(prefix.length()).split("\\.", -1);
        if (path.length != 3 || path[0].isBlank() || path[1].isBlank() || path[2].isBlank()) {
            throw new IllegalArgumentException("Expected " + prefix + "<app>.<deployment>.<parameter>");
        }
        if (!PARAMETERS.contains(path[2])) {
            source.getLogger().warnf("Dependency endpoint requested an unknown parameter: '%s'", property);
            return null;
        }

        DependencyEndpointsConfig versioned = privateEndpoints
                ? clowderConfig.privateDependencyEndpoints : clowderConfig.dependencyEndpoints;
        List<? extends EndpointConfig> legacy = privateEndpoints
                ? clowderConfig.privateEndpoints : clowderConfig.endpoints;
        Map<String, Map<String, DependencyEndpointConfig>> v2 = versioned == null ? null : versioned.v2;
        ResolvedEndpoint endpoint = resolve(v2, legacy, path[0], path[1]);
        if (endpoint == null) {
            if (v2 == null && legacy == null) {
                if (optional) {
                    // An empty string suppresses expression defaults such as authenticated:false.
                    return null;
                }
                throw new IllegalStateException("No " + (privateEndpoints ? "private " : "")
                        + "dependency endpoints section found for " + property);
            }
            return null;
        }

        return switch (path[2]) {
            case "uri" -> endpoint.uri();
            case "authenticated" -> Boolean.toString(endpoint.authenticated());
            case "ca-certificate" -> endpoint.caCertificate();
            case "trust-store-path" -> endpoint.legacyTls() ? source.getTrustStorePath()
                    : endpoint.caCertificate() == null ? null : source.getTrustStorePath(endpoint.caCertificate());
            case "trust-store-password" -> endpoint.legacyTls() ? source.getTrustStorePassword()
                    : endpoint.caCertificate() == null ? null : source.getTrustStorePassword(endpoint.caCertificate());
            case "trust-store-type" -> endpoint.legacyTls() || endpoint.caCertificate() != null
                    ? source.getTrustStoreType() : null;
            default -> null;
        };
    }

    private ResolvedEndpoint resolve(Map<String, Map<String, DependencyEndpointConfig>> v2,
            List<? extends EndpointConfig> legacy, String app, String deployment) {
        Map<String, DependencyEndpointConfig> deployments = v2 == null ? null : v2.get(app);
        DependencyEndpointConfig endpoint = deployments == null ? null : deployments.get(deployment);
        if (endpoint != null && endpoint.uri != null && !endpoint.uri.isBlank()) {
            // Invalid populated V2 metadata must not silently downgrade to a V1 endpoint.
            validate(endpoint, app, deployment);
            String ca = endpoint.caCertificate == null || endpoint.caCertificate.isBlank()
                    ? null : endpoint.caCertificate;
            return new ResolvedEndpoint(endpoint.uri, endpoint.authenticated, ca, false);
        }

        if (legacy != null) {
            for (EndpointConfig candidate : legacy) {
                if (app.equals(candidate.app) && deployment.equals(candidate.name)) {
                    boolean tls = candidate.tlsPort != null && candidate.tlsPort != 0;
                    String uri = String.format("%s://%s:%s", tls ? "https" : "http", candidate.hostname,
                            tls ? candidate.tlsPort : candidate.port);
                    return new ResolvedEndpoint(uri, false, tls ? clowderConfig.tlsCAPath : null, tls);
                }
            }
        }
        return null;
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

    private record ResolvedEndpoint(String uri, boolean authenticated, String caCertificate, boolean legacyTls) { }
}
