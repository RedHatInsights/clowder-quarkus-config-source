package com.redhat.cloud.common.clowder.configsource.utils;

import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CertUtils {

    public static final Logger LOG = Logger.getLogger(CertUtils.class.getName());

    private CertUtils() {

    }

    public static String createTempCertFile(String fileName, String certData) {
        byte[] cert = certData.getBytes(StandardCharsets.UTF_8);

        try {
            File certFile = createTempFile(fileName, ".crt");
            return Files.write(Path.of(certFile.getAbsolutePath()), cert).toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Certificate file creation failed", e);
        }
    }

    public static File createTempFile(String fileName, String suffix) throws IOException {
        File file = File.createTempFile(fileName, suffix);

        try {
            file.deleteOnExit();
        } catch (SecurityException e) {
            LOG.warnf(e, "Delete on exit of the '%s' cert file denied by the security manager", fileName);
        }

        return file;
    }
}
