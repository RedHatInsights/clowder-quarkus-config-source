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

/**
 * This factory obtains the already existing config properties and values
 * and feeds them into our new Clowder ConfigSource so that they can be
 * mangled there is needed.
 */
public class ClowderConfigSourceFactory implements ConfigSourceFactory {

    Logger log = Logger.getLogger(getClass().getName());

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext configSourceContext) {

        ConfigValue cv = configSourceContext.getValue("clowder.file");
        String clowderConfig;
        if (cv != null && cv.getValue() != null) {
            clowderConfig = cv.getValue();
        } else {
            clowderConfig = "/cdapp/cdappconfig.json";
        }
        log.info("Using ClowderConfigSource with config at " + clowderConfig);

        // It should be used, so get the existing key-values and
        // supply them to our source.
        Map<String, ConfigValue> exProp = new HashMap<>();
        Iterator<String> stringIterator = configSourceContext.iterateNames();
        while (stringIterator.hasNext()) {
            String key = stringIterator.next();
            ConfigValue value = configSourceContext.getValue(key);
            exProp.put(key,value);
        }

        return Collections.singletonList(new com.redhat.cloud.common.clowder.configsource.ClowderConfigSource(clowderConfig, exProp));
    }

    @Override
    public OptionalInt getPriority() {
        // This is the order of factory evaluation in case there are multiple
        // factories.
        return OptionalInt.of(270);
    }
}
