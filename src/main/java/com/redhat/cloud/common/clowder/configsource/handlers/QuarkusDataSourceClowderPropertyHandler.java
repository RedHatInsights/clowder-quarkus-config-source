package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;
import com.redhat.cloud.common.clowder.configsource.DatabaseConfig;

import static com.redhat.cloud.common.clowder.configsource.utils.CertUtils.createTempCertFile;

public class QuarkusDataSourceClowderPropertyHandler extends ClowderPropertyHandler {

    private static final String QUARKUS_DATASOURCE = "quarkus.datasource.";
    private static final String QUARKUS_DATASOURCE_JDBC_URL = "quarkus.datasource.jdbc.url";

    public QuarkusDataSourceClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public boolean handles(String property) {
        return property.startsWith(QUARKUS_DATASOURCE);
    }

    @Override
    public String handle(String property, ClowderConfigSource configSource) {
        if (clowderConfig.database == null) {
            throw new IllegalStateException("No database section found");
        }

        String item = property.substring(QUARKUS_DATASOURCE.length());
        if (item.equals("username")) {
            return clowderConfig.database.username;
        }

        String sslMode = clowderConfig.database.sslMode;
        boolean useSsl = !sslMode.equals("disable");
        boolean verifyFull = sslMode.equals("verify-full");
        if (item.equals("password")) {
            return clowderConfig.database.password;
        }

        if (item.equals("jdbc.url")) {
            String hostPortDb = getHostPortDb(clowderConfig.database);
            String tracing = "";
            String jdbcUrl = configSource.getExistingValue(QUARKUS_DATASOURCE_JDBC_URL);
            if (jdbcUrl != null) {
                if (jdbcUrl.contains(":tracing:")) {
                    // TODO Remove this block (tracing) later.
                    configSource.getLogger().warn("The support of OpenTracing in this library is deprecated and will be removed soon. Please consider switching to OpenTelemetry.");
                    tracing = "tracing:";
                } else if (jdbcUrl.contains(":otel:")) {
                    /*
                     * The existing JDBC URL is the one coming from the application.properties file.
                     * If that URL contains "otel" then it means that the app is able to connect to
                     * the database through the OpenTelemetry JDBC layer.
                     */
                    tracing = "otel:";
                }
            }

            jdbcUrl = String.format("jdbc:%s%s", tracing, hostPortDb);
            if (useSsl) {
                jdbcUrl = jdbcUrl + "?sslmode=" + sslMode;
            }
            if (verifyFull) {
                jdbcUrl = jdbcUrl + "&sslrootcert=" + createTempRdsCertFile(clowderConfig.database.rdsCa);
            }
            return jdbcUrl;
        }
        if (item.startsWith("reactive.")) {
            if (item.equals("reactive.url")) {
                return getHostPortDb(clowderConfig.database);
            }
            if (item.equals("reactive.postgresql.ssl-mode")) {
                return sslMode;
            }
            if (verifyFull) {
                if (item.equals("reactive.hostname-verification-algorithm")) {
                    return "HTTPS";
                }
                if (item.equals("reactive.trust-certificate-pem")) {
                    return "true";
                }
                if (item.equals("reactive.trust-certificate-pem.certs")) {
                    return createTempRdsCertFile(clowderConfig.database.rdsCa);
                }
            }
        }

        return configSource.getExistingValue(property);
    }

    private String getHostPortDb(DatabaseConfig database) {
        return String.format("postgresql://%s:%d/%s",
                database.hostname,
                database.port,
                database.name);
    }

    private String createTempRdsCertFile(String certData) {
        if (certData != null) {
            return createTempCertFile("rds-ca-root", certData);
        } else {
            throw new IllegalStateException("'database.sslMode' is set to 'verify-full' in the Clowder config but the 'database.rdsCa' field is missing");
        }
    }
}
