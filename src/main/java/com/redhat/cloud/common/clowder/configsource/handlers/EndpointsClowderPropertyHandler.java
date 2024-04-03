package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;
import com.redhat.cloud.common.clowder.configsource.EndpointConfig;

import java.util.List;

public class EndpointsClowderPropertyHandler extends ClowderPropertyHandler {

    private static final String CLOWDER_ENDPOINTS = "clowder.endpoints.";
    private static final String CLOWDER_ENDPOINTS_PARAM_URL = "url";
    private static final String CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PATH = "trust-store-path";
    private static final String CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PASSWORD = "trust-store-password";
    private static final String CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_TYPE = "trust-store-type";
    private static final Integer PORT_NOT_SET = 0;

    public EndpointsClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public boolean handles(String property) {
        return property.startsWith(getPropertyEndpointKey());
    }

    @Override
    public String handle(String property, ClowderConfigSource configSource) {
        try {
            if (clowderConfig.endpoints == null) {
                throw new IllegalStateException("No endpoints section found");
            }

            return processEndpoints(property, configSource, clowderConfig.endpoints, "Endpoint");
        } catch (IllegalStateException e) {
            configSource.getLogger().errorf("Failed to load config key '%s' from the Clowder configuration: %s", property, e.getMessage());
            throw e;
        }
    }

    protected String getPropertyEndpointKey() {
        return CLOWDER_ENDPOINTS;
    }

    /**
     * Attempts to find the corresponding value for the provided configuration
     * key. If the URL parameter has been requested, then depending on whether
     * the endpoint has a TLS port or not, a full URL including the protocol
     * is returned. For the rest of parameters it returns the corresponding
     * value.
     * @param configKey       the configuration key specified in the
     *                        "application.properties" file.
     * @param endpointType    the type of the endpoint which the function will
     *                        process. Used mainly for error and log messages.
     * @return a full URL including the protocol in the case of requesting an
     * endpoint's URL parameter. Regular values for the rest of the parameters.
     */
    protected String processEndpoints(String configKey, ClowderConfigSource configSource, List<? extends EndpointConfig> endpoints, String endpointType) {
        final String clowderKey = getPropertyEndpointKey();
        final String requestedEndpointConfig = configKey.substring(clowderKey.length());
        final String[] configPath = requestedEndpointConfig.split("\\.");

        final String requestedEndpoint;
        final String param;
        final String FORMAT_EXAMPLE = String.format("[%s].[url|trust-store-path|trust-store-password|trust-store-type]", endpointType);
        if (configPath.length == 1) {
            configSource.getLogger().warnf("%s '%s' is using the old format. Please move to the new one: %s", endpointType, requestedEndpointConfig, FORMAT_EXAMPLE);
            requestedEndpoint = configPath[0];
            param = CLOWDER_ENDPOINTS_PARAM_URL;
        } else if (configPath.length != 2) {
            throw new IllegalArgumentException(String.format("%s '%s' expects a different format: %s", endpointType, requestedEndpointConfig, FORMAT_EXAMPLE));
        } else {
            requestedEndpoint = configPath[0];
            param = configPath[1];
        }

        EndpointConfig endpointConfig = null;

        for (final EndpointConfig configCandidate : endpoints) {
            final String currentEndpoint = String.format("%s-%s", configCandidate.app, configCandidate.name);
            if (currentEndpoint.equals(requestedEndpoint)) {
                endpointConfig = configCandidate;
                break;
            }
        }

        if (endpointConfig == null) {
            configSource.getLogger().warnf("%s '%s' not found in the %s section", endpointType, requestedEndpoint, clowderKey.substring(0, clowderKey.length() - 1));
            return null;
        }

        switch (param) {
            case CLOWDER_ENDPOINTS_PARAM_URL:
                if (usesTls(endpointConfig)) {
                    return String.format("https://%s:%s", endpointConfig.hostname, endpointConfig.tlsPort);
                } else {
                    return String.format("http://%s:%s", endpointConfig.hostname, endpointConfig.port);
                }
            case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PATH:
                if (usesTls(endpointConfig)) {
                    return configSource.getTrustStorePath();
                }

                return null;
            case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_PASSWORD:
                if (usesTls(endpointConfig)) {
                    return configSource.getTrustStorePassword();
                }

                return null;
            case CLOWDER_ENDPOINTS_PARAM_TRUST_STORE_TYPE:
                if (usesTls(endpointConfig)) {
                    return configSource.getTrustStoreType();
                }

                return null;
            default:
                configSource.getLogger().warnf("%s '%s' requested an unknown param: '%s'", endpointType, requestedEndpoint, param);
                return null;
        }
    }

    private boolean usesTls(EndpointConfig endpointConfig) {
        return endpointConfig.tlsPort != null && !endpointConfig.tlsPort.equals(PORT_NOT_SET);
    }
}
