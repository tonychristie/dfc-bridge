package com.spirecentral.dfcbridge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Holds one DFC instance per docbroker. Each instance is a classloader over the DFC jars
 * with its own dfc.properties, which lists only that docbroker.
 */
@Service
public class DfcInstanceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DfcInstanceRegistry.class);

    private static final String DFC_PROPERTIES = "dfc.properties";
    private static final String DFC_KEYSTORE = "dfc.keystore";
    private static final String DOCBROKER_PREFIX = "dfc.docbroker.";
    private static final String DATA_DIR = "dfc.data.dir";

    private final Map<List<Object>, ClassLoader> loaders = new ConcurrentHashMap<>();
    private final String dfcHome;
    private final String dfcConfig;
    private final Path instancesDir;

    @Autowired
    public DfcInstanceRegistry(@Value("${dfc.home:}") String dfcHome, @Value("${dfc.config:}") String dfcConfig) {
        this(dfcHome, dfcConfig, Path.of("dfc-instances"));
    }

    DfcInstanceRegistry(String dfcHome, String dfcConfig, Path instancesDir) {
        this.dfcHome = dfcHome;
        this.dfcConfig = dfcConfig;
        this.instancesDir = instancesDir;
    }

    public ClassLoader loaderFor(String host, int port) {
        return loaders.computeIfAbsent(List.of(host, port), key -> createLoader(host, port));
    }

    private ClassLoader createLoader(String host, int port) {
        String name = host.replaceAll("[^A-Za-z0-9.-]", "_") + "_" + port;
        Path instanceDir = instancesDir.resolve(name).toAbsolutePath();
        Path configDir = instanceDir.resolve("config");
        Path dataDir = instanceDir.resolve("data");
        try {
            Files.createDirectories(configDir);
            Files.createDirectories(dataDir);
            writeDfcProperties(configDir, dataDir, host, port);
            copyKeystore(configDir);

            List<URL> urls = new ArrayList<>();
            urls.add(configDir.toUri().toURL());
            for (Path jar : dfcJars()) {
                urls.add(jar.toUri().toURL());
            }
            log.info("Created DFC instance for docbroker {}:{} in {}", host, port, instanceDir);
            return new URLClassLoader("dfc-" + name, urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the DFC instance in " + instanceDir, e);
        }
    }

    private void writeDfcProperties(Path configDir, Path dataDir, String host, int port) throws IOException {
        Properties properties = new Properties();
        Optional<Path> source = configFile(DFC_PROPERTIES);
        if (source.isPresent()) {
            try (InputStream in = Files.newInputStream(source.get())) {
                properties.load(in);
            }
        }
        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(DOCBROKER_PREFIX))
                .forEach(properties::remove);
        properties.setProperty(DOCBROKER_PREFIX + "host[0]", host);
        properties.setProperty(DOCBROKER_PREFIX + "port[0]", String.valueOf(port));
        properties.setProperty(DATA_DIR, dataDir.toString());
        try (OutputStream out = Files.newOutputStream(configDir.resolve(DFC_PROPERTIES))) {
            properties.store(out, null);
        }
    }

    private void copyKeystore(Path configDir) throws IOException {
        Optional<Path> keystore = configFile(DFC_KEYSTORE);
        if (keystore.isPresent()) {
            Files.copy(keystore.get(), configDir.resolve(DFC_KEYSTORE), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Optional<Path> configFile(String fileName) {
        if (dfcConfig.isBlank()) {
            return Optional.empty();
        }
        Path file = Path.of(dfcConfig, fileName);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    private List<Path> dfcJars() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(dfcHome))) {
            return files
                    .filter(file -> {
                        String fileName = file.getFileName().toString();
                        return fileName.endsWith(".jar") && !fileName.startsWith("servlet-api");
                    })
                    .sorted()
                    .map(Path::toAbsolutePath)
                    .toList();
        }
    }
}
