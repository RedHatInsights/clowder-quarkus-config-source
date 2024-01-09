package com.redhat.cloud.common.clowder.configsource;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalInt;

import static io.smallrye.config.Expressions.withoutExpansion;

/**
 * This factory obtains the already existing config properties and values
 * and feeds them into our new Clowder ConfigSource so that they can be
 * mangled there is needed.
 */
public class ClowderConfigSourceFactory implements ConfigSourceFactory {

    Logger log = Logger.getLogger(getClass().getName());

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext configSourceContext) {

        ConfigValue cv = configSourceContext.getValue("acg.config");
        ConfigValue exposeKafkaSslConfigKeysCv = configSourceContext.getValue("feature-flags.expose-kafka-ssl-config-keys.enabled");
        String clowderConfig = "/cdapp/cdappconfig.json";
        boolean exposeKafkaSslConfigKeys = false;
        if (cv != null && cv.getValue() != null) {
            clowderConfig = cv.getValue();
        }
        log.info("Using ClowderConfigSource with config at " + clowderConfig);

        if (exposeKafkaSslConfigKeysCv != null && exposeKafkaSslConfigKeysCv.getValue() != null) {
            exposeKafkaSslConfigKeys = Boolean.valueOf(exposeKafkaSslConfigKeysCv.getValue());
        }
        log.info("Expose Kafka config keys: " + exposeKafkaSslConfigKeys);

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
                ConfigValue value = configSourceContext.getValue(key);
                exProp.put(key, value);
            }
        });

        return Collections.singletonList(new ClowderConfigSource(clowderConfig, exProp, exposeKafkaSslConfigKeys));
    }

    @Override
    public OptionalInt getPriority() {
        // This is the order of factory evaluation in case there are multiple
        // factories.
        return OptionalInt.of(270);
    }
}
