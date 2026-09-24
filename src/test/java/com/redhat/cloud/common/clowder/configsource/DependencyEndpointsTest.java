package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSourceFactory.loadPropertyHandlers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DependencyEndpointsTest {

    private static final String PUBLIC = "clowder.dependency-endpoints.";
    private static final String PRIVATE = "clowder.private-dependency-endpoints.";
    private static final String OPTIONAL = "clowder.optional-dependency-endpoints.";
    private static final String OPTIONAL_PRIVATE = "clowder.optional-private-dependency-endpoints.";
    private ClowderConfig root;

    @TempDir
    Path temp;

    @BeforeEach
    void setup() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/cdappconfig_v2.json")) {
            root = new ObjectMapper().readValue(input, ClowderConfig.class);
        }
    }

    @Test
    void publicV2TakesPrecedenceAndKeepsTheCompleteUri() {
        ClowderConfigSource source = source();
        assertEquals("https://rbac.remote.example:443/base", source.getValue(PUBLIC + "rbac.service.uri"));
        assertEquals("true", source.getValue(PUBLIC + "rbac.service.authenticated"));
        // Opt-in must not redirect clients that still use the original V1 properties.
        assertEquals("https://rbac.svc:8443", source.getValue("clowder.endpoints.rbac-service.url"));
    }

    @Test
    void privateV2TakesPrecedence() {
        ClowderConfigSource source = source();
        assertEquals("https://export.remote.example:443", source.getValue(PRIVATE + "export-service.service.uri"));
        assertEquals("true", source.getValue(PRIVATE + "export-service.service.authenticated"));
        assertEquals("http://export.svc:10000", source.getValue("clowder.private-endpoints.export-service-service.url"));
    }

    @Test
    void allFourLookupsWorkWithoutV1Arrays() {
        root.endpoints = null;
        root.privateEndpoints = null;
        ClowderConfigSource source = source();
        for (String prefix : new String[] {PUBLIC, OPTIONAL}) {
            assertEquals("https://rbac.remote.example:443/base", source.getValue(prefix + "rbac.service.uri"));
        }
        for (String prefix : new String[] {PRIVATE, OPTIONAL_PRIVATE}) {
            assertEquals("https://export.remote.example:443", source.getValue(prefix + "export-service.service.uri"));
        }
    }

    @Test
    void v1OnlyFallbackPreservesTlsAndPrivateEndpoints() {
        root.dependencyEndpoints = null;
        root.privateDependencyEndpoints = null;
        ClowderConfigSource source = source();
        for (String prefix : new String[] {PUBLIC, OPTIONAL}) {
            assertEquals("https://rbac.svc:8443", source.getValue(prefix + "rbac.service.uri"));
            assertEquals("false", source.getValue(prefix + "rbac.service.authenticated"));
            assertEquals(root.tlsCAPath, source.getValue(prefix + "rbac.service.ca-certificate"));
            assertEquals(source.getTrustStorePath(), source.getValue(prefix + "rbac.service.trust-store-path"));
        }
        for (String prefix : new String[] {PRIVATE, OPTIONAL_PRIVATE}) {
            assertEquals("http://export.svc:10000", source.getValue(prefix + "export-service.service.uri"));
            assertEquals("false", source.getValue(prefix + "export-service.service.authenticated"));
            assertNull(source.getValue(prefix + "export-service.service.trust-store-path"));
        }
    }

    @Test
    void fallbackIsPerEndpointNotPerSection() {
        assertEquals("http://legacy.svc:8000", source().getValue(PUBLIC + "legacy.service.uri"));
        root.dependencyEndpoints.v2.get("rbac").remove("service");
        assertEquals("https://rbac.svc:8443", source().getValue(PUBLIC + "rbac.service.uri"));
    }

    @Test
    void missingOrBlankV2UriFallsBackAsOneMetadataUnit() {
        DependencyEndpointConfig endpoint = root.dependencyEndpoints.v2.get("rbac").get("service");
        endpoint.caCertificate = "unused-v2-ca.pem";
        for (String absentUri : new String[] {null, "", " "}) {
            endpoint.uri = absentUri;
            ClowderConfigSource source = source();
            assertEquals("https://rbac.svc:8443", source.getValue(PUBLIC + "rbac.service.uri"));
            assertEquals("false", source.getValue(PUBLIC + "rbac.service.authenticated"));
            assertEquals(root.tlsCAPath, source.getValue(PUBLIC + "rbac.service.ca-certificate"));
        }
    }

    @Test
    void futureVersionsDoNotPreventV1Fallback() {
        root.dependencyEndpoints.v2 = null;
        assertEquals("https://rbac.svc:8443", source().getValue(PUBLIC + "rbac.service.uri"));
    }

    @Test
    void noV2CaMeansSystemTrustEvenWithGlobalCa() {
        ClowderConfigSource source = source();
        assertNotNull(source.getTrustStorePath()); // Initialize the legacy store first.
        for (String parameter : new String[] {"ca-certificate", "trust-store-path", "trust-store-password", "trust-store-type"}) {
            assertNull(source.getValue(PUBLIC + "rbac.service." + parameter));
            assertNull(source.getValue(PRIVATE + "export-service.service." + parameter));
        }
    }

    @Test
    void blankV2CaAlsoUsesSystemTrust() {
        root.dependencyEndpoints.v2.get("rbac").get("service").caCertificate = " ";
        assertNull(source().getValue(PUBLIC + "rbac.service.trust-store-path"));
    }

    @Test
    void endpointTruststoresContainOnlyTheirOwnCaAndAreCached() throws Exception {
        ClowderConfigSource source = source();
        String first = source.getValue(PUBLIC + "local.one.trust-store-path");
        String second = source.getValue(PUBLIC + "local.two.trust-store-path");
        assertNotEquals(first, second);
        assertEquals(first, source.getValue(PUBLIC + "local.one.trust-store-path"));
        assertEquals(second, source.getValue(PUBLIC + "local.two.trust-store-path"));
        assertStoreContainsOnly(source, PUBLIC + "local.one.", "cert01.pem");
        assertStoreContainsOnly(source, PUBLIC + "local.two.", "cert02.pem");
        assertEquals("target/test-classes/cert02.pem", source.getValue(PUBLIC + "local.two.ca-certificate"));
    }

    @Test
    void privateEndpointsUseTheirOwnCaInsteadOfGlobalCa() throws Exception {
        root.privateDependencyEndpoints.v2.get("export-service").get("service").caCertificate = "target/test-classes/cert02.pem";
        ClowderConfigSource source = source();
        assertStoreContainsOnly(source, OPTIONAL_PRIVATE + "export-service.service.", "cert02.pem");
        assertNotEquals(source.getTrustStorePath(), source.getValue(PRIVATE + "export-service.service.trust-store-path"));
    }

    @Test
    void unreadableCaDoesNotFallBackToGlobalTrust() {
        root.dependencyEndpoints.v2.get("rbac").get("service").caCertificate = temp.resolve("missing.pem").toString();
        ClowderConfigSource source = source();
        assertThrows(IllegalStateException.class, () -> source.getValue(PUBLIC + "rbac.service.trust-store-path"));
    }

    @Test
    void invalidCaDoesNotFallBackToGlobalTrust() throws Exception {
        Path invalid = temp.resolve("invalid.pem");
        Files.writeString(invalid, "-----BEGIN CERTIFICATE-----\nnot a certificate!\n-----END CERTIFICATE-----");
        root.dependencyEndpoints.v2.get("rbac").get("service").caCertificate = invalid.toString();
        assertThrows(IllegalStateException.class, () -> source().getValue(PUBLIC + "rbac.service.trust-store-path"));
    }

    @Test
    void v1TlsStillRequiresGlobalCa() {
        root.dependencyEndpoints = null;
        root.tlsCAPath = null;
        assertThrows(IllegalStateException.class, () -> source().getValue(PUBLIC + "rbac.service.trust-store-path"));
    }

    @Test
    void inClusterUnauthenticatedHttpAndHyphenatedNamesWork() {
        ClowderConfigSource source = source();
        String endpoint = PUBLIC + "app-with-hyphens.service-with-hyphens.";
        assertEquals("http://plain.svc:8000", source.getValue(endpoint + "uri"));
        assertEquals("false", source.getValue(endpoint + "authenticated"));
        assertNull(source.getValue(endpoint + "trust-store-path"));
    }

    @Test
    void absentRequiredSectionsFailAndAbsentOptionalSectionsReturnNull() {
        root = new ClowderConfig();
        ClowderConfigSource source = source();
        for (String prefix : new String[] {PUBLIC, PRIVATE}) {
            assertThrows(IllegalStateException.class, () -> source.getValue(prefix + "missing.service.uri"));
        }
        for (String prefix : new String[] {OPTIONAL, OPTIONAL_PRIVATE}) {
            assertNull(source.getValue(prefix + "missing.service.uri"));
        }
    }

    @Test
    void missingEndpointInPresentSectionReturnsNull() {
        ClowderConfigSource source = source();
        for (String prefix : new String[] {PUBLIC, OPTIONAL, PRIVATE, OPTIONAL_PRIVATE}) {
            assertNull(source.getValue(prefix + "missing.service.uri"));
        }
    }

    @Test
    void publicAndPrivateDoNotCrossFallback() {
        assertNull(source().getValue(PUBLIC + "export-service.service.uri"));
        assertNull(source().getValue(PRIVATE + "rbac.service.uri"));
    }

    @Test
    void invalidV2UriFailsInsteadOfDowngradingToV1() {
        DependencyEndpointConfig endpoint = root.dependencyEndpoints.v2.get("rbac").get("service");
        for (String invalid : new String[] {"bad uri", "//host:443", "ftp://host:443", "https://user:secret@host", "https://host/#fragment"}) {
            endpoint.uri = invalid;
            assertThrows(IllegalStateException.class, () -> source().getValue(PUBLIC + "rbac.service.uri"));
        }
    }

    @Test
    void missingAuthenticationFlagFailsInsteadOfAssumingUnauthenticated() {
        root.dependencyEndpoints.v2.get("rbac").get("service").authenticated = null;
        assertThrows(IllegalStateException.class, () -> source().getValue(PUBLIC + "rbac.service.uri"));
        assertThrows(IllegalStateException.class, () -> source().getValue(PUBLIC + "rbac.service.authenticated"));
    }

    @Test
    void malformedKeysFailAndUnknownParametersReturnNull() {
        ClowderConfigSource source = source();
        for (String invalid : new String[] {"rbac.uri", "rbac..uri", "rbac.service.", ".service.uri", "rbac.service.extra.uri"}) {
            assertThrows(IllegalArgumentException.class, () -> source.getValue(PUBLIC + invalid));
        }
        assertNull(source.getValue(PUBLIC + "rbac.service.unknown"));
    }

    @Test
    void nullDeploymentMapsFallBackWithoutCrashing() {
        root.dependencyEndpoints.v2 = new HashMap<>(Map.of("other", Map.of()));
        root.dependencyEndpoints.v2.put("rbac", null);
        assertEquals("https://rbac.svc:8443", source().getValue(PUBLIC + "rbac.service.uri"));
    }

    private ClowderConfigSource source() {
        return new ClowderConfigSource(root, new HashMap<>(), loadPropertyHandlers(root, false));
    }

    private void assertStoreContainsOnly(ClowderConfigSource source, String prefix, String caFile) throws Exception {
        KeyStore store = KeyStore.getInstance(source.getValue(prefix + "trust-store-type"));
        try (InputStream input = Files.newInputStream(Path.of(source.getValue(prefix + "trust-store-path")));
                InputStream expected = getClass().getResourceAsStream("/" + caFile)) {
            store.load(input, source.getValue(prefix + "trust-store-password").toCharArray());
            Collection<? extends Certificate> certificates = CertificateFactory.getInstance("X.509").generateCertificates(expected);
            assertEquals(certificates.size(), store.size());
            for (Certificate certificate : certificates) {
                assertNotNull(store.getCertificateAlias(certificate));
            }
        }
    }
}
