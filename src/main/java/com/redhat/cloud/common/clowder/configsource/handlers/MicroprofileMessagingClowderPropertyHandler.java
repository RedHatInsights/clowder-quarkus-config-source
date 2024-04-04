package com.redhat.cloud.common.clowder.configsource.handlers;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;
import com.redhat.cloud.common.clowder.configsource.TopicConfig;

public class MicroprofileMessagingClowderPropertyHandler extends ClowderPropertyHandler {

    public MicroprofileMessagingClowderPropertyHandler(ClowderConfig clowderConfig) {
        super(clowderConfig);
    }

    @Override
    public boolean handles(String property) {
        return property.startsWith("mp.messaging") && property.endsWith(".topic");
    }

    @Override
    public String handle(String property, ClowderConfigSource configSource) {
        if (clowderConfig.kafka == null) {
            throw new IllegalStateException("Kafka base object not present, can't set Kafka values");
        }
        // We need to find the replaced topic by first finding
        // the requested name and then getting the replaced name
        String requested = configSource.getExistingValue(property);
        for (TopicConfig topic : clowderConfig.kafka.topics) {
            if (topic.requestedName.equals(requested)) {
                return topic.name;
            }
        }

        return requested;
    }
}
