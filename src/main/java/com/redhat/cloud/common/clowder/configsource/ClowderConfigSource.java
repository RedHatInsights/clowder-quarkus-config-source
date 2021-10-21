package com.redhat.cloud.common.clowder.configsource;

import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Config source that is using the ClowderAppConfig
 */
public class ClowderConfigSource implements ConfigSource {

    public static final String CLOWDER_CONFIG_SOURCE = "ClowderConfigSource";

    private static final String QUARKUS_LOG_CLOUDWATCH = "quarkus.log.cloudwatch";
    private static final String QUARKUS_DATASOURCE_JDBC_URL = "quarkus.datasource.jdbc.url";
    private static final String CLOWDER_ENDPOINTS = "clowder.endpoints.";

    Logger log = Logger.getLogger(getClass().getName());
    private final Map<String, ConfigValue> existingValues;
    JsonObject root;
    private boolean translate = true;

    /**
     * <p>Constructor for ClowderConfigSource.</p>
     *
     * @param configFile Name/Path of a file to read the config from.
     * @param exProp {@link java.util.Map} containing the existing properties from e.g. application.properties.
     */
    public ClowderConfigSource(String configFile, Map<String, ConfigValue> exProp) {

        existingValues = exProp;
        File file = new File(configFile);
        if (!file.canRead()) {
            log.warn("Can't read clowder config from " + file.getAbsolutePath() + ", not doing translations.");
            translate = false;
        }
        else {
            try (FileInputStream fis = new FileInputStream(file)) {
                JsonReader reader = Json.createReader(fis);
                root = reader.readObject();
            } catch (IOException ioe) {
                log.warn("Reading the clowder config failed, not doing translations: " + ioe.getMessage());
                translate = false;
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

        // This matches against the property as in application.properties
        // For profiles != prod, values are requested first like
        // %<profile>.property. E.g. %dev.quarkus.http.port

        if (translate) {

            if (configKey.equals("quarkus.http.port")) {
                JsonNumber webPort = root.getJsonNumber("webPort");
                return webPort.toString();
            }
            JsonObject kafkaBase = root.getJsonObject("kafka");
            if (configKey.equals("kafka.bootstrap.servers")) {
                if (kafkaBase==null) {
                    throw new IllegalStateException("Kafka base object not present, can't set Kafka values");
                }
                JsonArray brokers = kafkaBase.getJsonArray("brokers");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < brokers.size(); i++) {
                    JsonObject broker = brokers.getJsonObject(i);
                    String br = broker.getString("hostname") + ":" + broker.getJsonNumber("port").toString();
                    sb.append(br);
                    if (i < brokers.size() - 1) {
                        sb.append(',');
                    }
                }
                return sb.toString();
            }

            if (configKey.startsWith("mp.messaging") && configKey.endsWith(".topic")) {
                if (kafkaBase==null) {
                    throw new IllegalStateException("Kafka base object not present, can't set Kafka values");
                }
                // We need to find the replaced topic by first finding
                // the requested name and then getting the replaced name
                String requested = existingValues.get(configKey).getValue();
                JsonArray topics = kafkaBase.getJsonArray("topics");
                for (int i = 0; i < topics.size(); i++) {
                    JsonObject aTopic = topics.getJsonObject(i);
                    if (aTopic.getString("requestedName").equals(requested)) {
                        String name = aTopic.getString("name");
                        return name;
                    }
                }
                return requested;
            }

            if (configKey.startsWith("quarkus.datasource")) {
                String item = configKey.substring("quarkus.datasource.".length());
                JsonObject dbObject = root.getJsonObject("database");
                if (dbObject == null) {
                    throw new IllegalStateException("No database section found");
                }
                if (item.equals("username")) {
                    return dbObject.getString("username");
                }
                String sslMode = dbObject.getString("sslMode");
                boolean useSsl = !sslMode.equals("disable");

                if (item.equals("password")) {
                    return dbObject.getString("password");
                }
                if (item.equals("jdbc.url")) {
                    String hostPortDb = getHostPortDb(dbObject);
                    String tracing = "";
                    if (existingValues.containsKey(QUARKUS_DATASOURCE_JDBC_URL)) {
                        String url = existingValues.get(QUARKUS_DATASOURCE_JDBC_URL).getValue();
                        if (url.contains(":tracing:")) {
                            tracing = "tracing:";
                        }
                    }
                    String jdbcUrl = String.format("jdbc:%s%s", tracing, hostPortDb);
                    if (useSsl) {
                        jdbcUrl = jdbcUrl + "?sslmode=" + sslMode;
                    }
                    return jdbcUrl;
                }
                if (item.equals("reactive.url")) {
                    String hostPortDb = getHostPortDb(dbObject);
                    if (useSsl) {
                        hostPortDb = hostPortDb + "?sslmode=" + sslMode;
                    }

                    return hostPortDb;
                }
            }

            if (configKey.startsWith(QUARKUS_LOG_CLOUDWATCH)) {
                JsonObject loggingObject = root.getJsonObject("logging");
                if (loggingObject == null) {
                    throw new IllegalStateException("No logging section found");
                }
                JsonObject cwObject = loggingObject.getJsonObject("cloudwatch");
                if (cwObject == null) {
                    throw new IllegalStateException("No cloudwatch section found in logging object");
                }
                int prefixLen = QUARKUS_LOG_CLOUDWATCH.length();
                String sub = configKey.substring(prefixLen+1);
                switch (sub) {
                    case "access-key-id":
                        return cwObject.getString("accessKeyId");
                    case "access-key-secret":
                        return cwObject.getString("secretAccessKey");
                    case "region":
                        return cwObject.getString("region");
                    case "log-group":
                        return cwObject.getString("logGroup");
                    default:
                        // fall through to fetching the value from application.properties
                }
            }

            if (configKey.startsWith(CLOWDER_ENDPOINTS)) {
                try {
                    JsonArray endpoints = root.getJsonArray("endpoints");
                    if (endpoints == null) {
                        throw new IllegalStateException("No endpoints section found");
                    }
                    String requestedEndpoint = configKey.substring(CLOWDER_ENDPOINTS.length());
                    for (int i = 0; i < endpoints.size(); i++) {
                        JsonObject endpoint = endpoints.getJsonObject(i);
                        String currentEndpoint = endpoint.getString("app") + "." + endpoint.getString("name");
                        if (currentEndpoint.equals(requestedEndpoint)) {
                            return endpoint.getString("hostname") + ":" + endpoint.getJsonNumber("port").intValue();
                        }
                    }
                    throw new IllegalStateException("Endpoint '" + requestedEndpoint + "' not found in the endpoints section");
                } catch (IllegalStateException e) {
                    log.errorf("Failed to load config key '%s' from the Clowder configuration: %s", configKey, e.getMessage());
                    throw e;
                }
            }
        }

        if (existingValues.containsKey(configKey)) {
            return existingValues.get(configKey).getValue();
        }
        else {
            return null;
        }
    }

    @Override
    public String getName() {
        return CLOWDER_CONFIG_SOURCE;
    }

    private String getHostPortDb(JsonObject dbObject) {
        String host = dbObject.getString("hostname");
        int port = dbObject.getJsonNumber("port").intValue();
        String dbName = dbObject.getString("name");

        return String.format("postgresql://%s:%d/%s",
                host,
                port,
                dbName);
    }
}
