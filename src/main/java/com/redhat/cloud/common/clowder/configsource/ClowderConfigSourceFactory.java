package com.redhat.cloud.common.clowder.configsource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.common.clowder.configsource.handlers.ClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.EndpointsClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.KafkaBootstrapServersClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.KafkaSecurityClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.MicroprofileMessagingClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.OptionalEndpointsClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.OptionalPrivateEndpointsClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.PrivateEndpointsClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.QuarkusDataSourceClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.QuarkusLogCloudWatchClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.QuarkusRedisClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.QuarkusUnleashClowderPropertyHandler;
import com.redhat.cloud.common.clowder.configsource.handlers.WebPortClowderPropertyHandler;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static io.smallrye.config.Expressions.withoutExpansion;

/**
 * This factory obtains the already existing config properties and values
 * and feeds them into our new Clowder ConfigSource so that they can be
 * mangled there is needed.
 */
public class ClowderConfigSourceFactory implements ConfigSourceFactory {

    private static final Logger LOG = Logger.getLogger(ClowderConfigSourceFactory.class.getName());

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext configSourceContext) {
        ConfigValue cv = configSourceContext.getValue("acg.config");
        String clowderConfig = "/cdapp/cdappconfig.json";
        if (cv != null && cv.getValue() != null) {
            clowderConfig = cv.getValue();
        }

        File clowderConfigFile = new File(clowderConfig);
        if (!clowderConfigFile.canRead()) {
            LOG.warn("Can't read clowder config from " + clowderConfigFile.getAbsolutePath() + ", not doing translations.");
            return List.of();
        }

        LOG.info("Using ClowderConfigSource with config at " + clowderConfig);
        return loadClowderConfigFromFile(configSourceContext, clowderConfigFile);
    }

    @Override
    public OptionalInt getPriority() {
        // This is the order of factory evaluation in case there are multiple
        // factories.
        return OptionalInt.of(270);
    }

    public static List<ClowderPropertyHandler> loadPropertyHandlers(ClowderConfig root, boolean exposeKafkaSslConfigKeys) {
        return List.of(new WebPortClowderPropertyHandler(root),
                new KafkaBootstrapServersClowderPropertyHandler(root),
                new KafkaSecurityClowderPropertyHandler(root, exposeKafkaSslConfigKeys),
                new QuarkusDataSourceClowderPropertyHandler(root),
                new QuarkusLogCloudWatchClowderPropertyHandler(root),
                new EndpointsClowderPropertyHandler(root),
                new OptionalEndpointsClowderPropertyHandler(root),
                new OptionalPrivateEndpointsClowderPropertyHandler(root),
                new PrivateEndpointsClowderPropertyHandler(root),
                new MicroprofileMessagingClowderPropertyHandler(root),
                new QuarkusUnleashClowderPropertyHandler(root),
                new QuarkusRedisClowderPropertyHandler(root));
    }

    private static List<ConfigSource> loadClowderConfigFromFile(ConfigSourceContext configSourceContext, File clowderConfigFile) {
        ConfigValue exposeKafkaSslConfigKeysCv = configSourceContext.getValue("feature-flags.expose-kafka-ssl-config-keys.enabled");
        boolean exposeKafkaSslConfigKeys = false;
        if (exposeKafkaSslConfigKeysCv != null
                && exposeKafkaSslConfigKeysCv.getValue() != null) {
            exposeKafkaSslConfigKeys = Boolean.parseBoolean(exposeKafkaSslConfigKeysCv.getValue());
        }

        try {
            String configJson = Files.readString(clowderConfigFile.toPath());
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            ClowderConfig root = objectMapper.readValue(configJson, ClowderConfig.class);

            LOG.info("Exposing Kafka config keys: " + exposeKafkaSslConfigKeys);
            List<ClowderPropertyHandler> handlers = loadPropertyHandlers(root, exposeKafkaSslConfigKeys);

            // It should be used, so get the existing key-values and
            // supply them to our source.
            Map<String, ConfigValue> exProp = new HashMap<>();
            Iterator<String> stringIterator = configSourceContext.iterateNames();

            /*
             * A config value expansion happens when an expression wrapped into `${ }` is resolved.
             * For example, if the configuration contains `foo=1234` and `bar=${foo}`, then `bar`
             * will be resolved and expanded to the value `1234`.
             * However, if an expression cannot be resolved because it refers to a missing config key,
             * then the expansion fails and a NoSuchElementException is thrown.
             * Such a throw should only happen when the config value is actually used and not here.
             * That's why we need to disable the config values expansion.
             */
            withoutExpansion(() -> {
                while (stringIterator.hasNext()) {
                    String key = stringIterator.next();
                    for (ClowderPropertyHandler handler : handlers) {
                        if (handler.handles(key)) {
                            ConfigValue value = configSourceContext.getValue(key);
                            exProp.put(key, value);
                            break;
                        }
                    }
                }
            });

            return Collections.singletonList(new ClowderConfigSource(root, exProp, handlers));
        } catch (IOException ex) {
            LOG.warn("Reading the clowder config failed, not doing translations", ex);
            return List.of();
        }
    }
}
