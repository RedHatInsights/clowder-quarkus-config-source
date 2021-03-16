package com.redhat.cloud.common.clowder.configsource;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 *
 */
public class CCSMain {


    public static void main(String[] args) throws Exception {

        Config config = ConfigProvider.getConfig();

              for (ConfigSource configSource : config.getConfigSources()) {
                  System.out.println(configSource.getName() + " -> " + configSource.getOrdinal());
                  if (configSource.getName().equals(ClowderConfigSource.CLOWDER_CONFIG_SOURCE)) {
                      return;
                  }
              }
        System.err.println("Not found");
    }
}
