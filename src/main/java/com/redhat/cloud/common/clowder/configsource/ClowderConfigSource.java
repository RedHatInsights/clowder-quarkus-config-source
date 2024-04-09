package com.redhat.cloud.common.clowder.configsource;

import com.redhat.cloud.common.clowder.configsource.handlers.ClowderPropertyHandler;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.redhat.cloud.common.clowder.configsource.utils.CertUtils.createTempFile;
import static com.redhat.cloud.common.clowder.configsource.utils.ComputedPropertiesUtils.PROPERTY_END;
import static com.redhat.cloud.common.clowder.configsource.utils.ComputedPropertiesUtils.PROPERTY_START;
import static com.redhat.cloud.common.clowder.configsource.utils.ComputedPropertiesUtils.getComputedProperties;
import static com.redhat.cloud.common.clowder.configsource.utils.ComputedPropertiesUtils.getPropertyFromSystem;
import static com.redhat.cloud.common.clowder.configsource.utils.ComputedPropertiesUtils.hasComputedProperties;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A Config source that is using the ClowderAppConfig
 */
public class ClowderConfigSource implements ConfigSource {

    public static final String CLOWDER_CONFIG_SOURCE = "ClowderConfigSource";
    private static final String CLOWDER_CERTIFICATE_STORE_TYPE = "PKCS12";
    private static final int DEFAULT_PASSWORD_LENGTH = 33;
    private static final String PROPERTY_DEFAULT = ":";
    private static final Logger LOG = Logger.getLogger(ClowderConfigSource.class.getName());

    private final ClowderConfig root;
    private final Map<String, ConfigValue> existingValues;
    private final List<ClowderPropertyHandler> handlers;

    private String trustStorePath;
    private String trustStorePassword;

    /**
     * <p>Constructor for ClowderConfigSource.</p>
     *
     * @param root                     The clowder config.
     * @param exProp                   {@link Map} containing the existing properties from e.g. application.properties.
     * @param handlers
     */
    public ClowderConfigSource(ClowderConfig root, Map<String, ConfigValue> exProp, List<ClowderPropertyHandler> handlers) {
        this.root = root;
        this.existingValues = exProp;
        this.handlers = handlers;

        // some handlers like KafkaSaslClowderPropertyHandler needs to populate extra properties that might
        // not be initially set by the users. So, we need to automatically expose these extra properties and
        // not overwrite them if it was already set by these users.
        for (ClowderPropertyHandler handler : handlers) {
            for (String property : handler.provides()) {
                try {
                    String value = getValue(property);
                    if (value != null && !value.isBlank()) {
                        existingValues.putIfAbsent(property, null);
                    }
                } catch (IllegalStateException ie) {
                    LOG.debug(ie.getMessage());
                }
            }
        }
    }

    @Override
    public Map<String, String> getProperties() {
        Map<String,String> props = new HashMap<>();
        Set<Map.Entry<String, ConfigValue>> entries = existingValues.entrySet();
        for (Map.Entry<String,ConfigValue> entry : entries) {
            String newVal = getValue(entry.getKey());
            if (newVal == null) {
                newVal = entry.getValue().getValue();
            }
            props.put(entry.getKey(),newVal);
        }

        return props;
    }

    @Override
    public Set<String> getPropertyNames() {
        return existingValues.keySet();
    }

    @Override
    public int getOrdinal() {
        // Provide a value higher than 250 to it overrides application.properties
        return 270;
    }

    /**
     * Return a value for a config property.
     * We need to look at the clowder provided data and eventually replace
     * the requested values from application.properties with what clowder
     * provides us, which may be different.
     *
     * If the configfile was bad, we return the existing values.
     */
    @Override
    public String getValue(String configKey) {
        for (ClowderPropertyHandler handler : handlers) {
            if (handler.handles(configKey)) {
                return handler.handle(configKey, this);
            }
        }

        return getExistingValue(configKey);
    }

    @Override
    public String getName() {
        return CLOWDER_CONFIG_SOURCE;
    }

    public Logger getLogger() {
        return LOG;
    }

    public String getExistingValue(String configKey) {
        return Optional.ofNullable(this.existingValues.get(configKey))
                .map(c -> resolveValue(c.getValue()))
                .orElse(null);
    }

    public String getTrustStorePassword() {
        if (trustStorePassword == null) {
            initializeTrustStoreCertificate();
        }

        return trustStorePassword;
    }

    public String getTrustStorePath() {
        if (trustStorePath == null) {
            initializeTrustStoreCertificate();
        }

        return trustStorePath;
    }

    public String getTrustStoreType() {
        return CLOWDER_CERTIFICATE_STORE_TYPE;
    }

    private void initializeTrustStoreCertificate() {
        ensureTlsCertPathIsPresent();

        try {
            String certContent = Files.readString(new File(root.tlsCAPath).toPath(), UTF_8);
            List<String> base64Certs = readCerts(certContent);

            List<X509Certificate> certificates = parsePemCert(base64Certs)
                    .stream()
                    .map(this::buildX509Cert)
                    .collect(Collectors.toList());

            if (certificates.size() < 1) {
                throw new IllegalStateException("Could not parse any certificate in the file");
            }

            // Generate a keystore by hand
            // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/KeyStore.html

            KeyStore truststore = KeyStore.getInstance(CLOWDER_CERTIFICATE_STORE_TYPE);

            // Per the docs, we need to init the new keystore with load(null)
            truststore.load(null);

            for (int i = 0; i < certificates.size(); i++) {
                truststore.setCertificateEntry("cert-" + i, certificates.get(i));
            }

            char[] password = buildPassword(base64Certs.get(0));
            this.trustStorePath = writeTruststore(truststore, password);
            this.trustStorePassword = new String(password);
        } catch (IOException ioe) {
            throw new IllegalStateException("Couldn't load the certificate, but we were requested a truststore", ioe);
        } catch (KeyStoreException kse) {
            throw new IllegalStateException("Couldn't load the keystore format PKCS12", kse);
        } catch (NoSuchAlgorithmException | CertificateException ce) {
            throw new IllegalStateException("Couldn't configure the keystore", ce);
        }
    }

    private void ensureTlsCertPathIsPresent() {
        if (root.tlsCAPath == null || root.tlsCAPath.isBlank()) {
            throw new IllegalStateException("Requested tls port for endpoint but did not provide tlsCAPath");
        }
    }

    static List<String> readCerts(String certString) {
        return Arrays.stream(certString.split("-----BEGIN CERTIFICATE-----"))
                .filter(s -> !s.isEmpty())
                .map(s -> Arrays.stream(s
                                .split("-----END CERTIFICATE-----"))
                        .filter(s2 -> !s2.isEmpty())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Invalid certificate found"))

                )
                .map(String::trim)
                .map(s -> s.replaceAll("\n", ""))
                .collect(Collectors.toList());
    }

    private List<byte[]> parsePemCert(List<String> base64Certs) {
        return base64Certs
                .stream()
                .map(cert -> Base64.getDecoder().decode(cert.getBytes(StandardCharsets.UTF_8)))
                .collect(Collectors.toList());
    }

    private X509Certificate buildX509Cert(byte[] cert) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(cert));
        } catch (CertificateException certificateException) {
            throw new IllegalStateException("Couldn't load the x509 certificate factory", certificateException);
        }
    }

    private String writeTruststore(KeyStore keyStore, char[] password) {
        try {
            File file = createTempFile("truststore", ".trust");
            keyStore.store(new FileOutputStream(file), password);
            return file.getAbsolutePath();
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException("Truststore creation failed", e);
        }
    }

    private char[] buildPassword(String seed) {
        // To avoid having a fixed password - fetch the first characters from a string (the certificate)
        int size = Math.min(DEFAULT_PASSWORD_LENGTH, seed.length());
        char[] password = new char[size];
        seed.getChars(0, size, password, 0);
        return password;
    }

    private String resolveValue(String property) {
        if (property == null || property.isEmpty() || !hasComputedProperties(property)) {
            return property;
        }

        for (String rawSystemProperty : getComputedProperties(property)) {
            String value = null;
            String systemProperty = rawSystemProperty;
            if (systemProperty.contains(PROPERTY_DEFAULT)) {
                int splitPosition = systemProperty.indexOf(PROPERTY_DEFAULT);
                value = systemProperty.substring(splitPosition + PROPERTY_DEFAULT.length());
                systemProperty = systemProperty.substring(0, splitPosition);

                if (hasComputedProperties(value)) {
                    value = resolveValue(value);
                }
            }

            ConfigValue computedValue = existingValues.get(systemProperty);
            if (computedValue != null) {
                value = computedValue.getValue();
            } else {
                // Check whether the system property is provided:
                value = getPropertyFromSystem(systemProperty, value);
            }

            if (value != null) {
                property = property.replace(PROPERTY_START + rawSystemProperty + PROPERTY_END, value);
            }
        }

        return property;
    }
}
