package com.spirecentral.dfcbridge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Holds one DFC instance per docbroker. Each instance is a classloader over the DFC jars
 * with its own dfc.properties, which lists only that docbroker.
 */
@Service
public class DfcInstanceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DfcInstanceRegistry.class);

    private static final String DFC_PROPERTIES = "dfc.properties";
    private static final String DOCBROKER_PREFIX = "dfc.docbroker.";
    private static final String DATA_DIR = "dfc.data.dir";
    private static final String INCLUDE = "#include";
    private static final Pattern INCLUDE_LINE = Pattern.compile("#include\\s+\\S.*");
    private static final Pattern INDEXED_DOCBROKER_KEY = Pattern.compile("dfc\\.docbroker\\.[a-z_]+\\[\\d+\\]");

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
            copyConfigFiles(configDir);

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
        List<String> lines = new ArrayList<>();
        Optional<Path> source = configFile(DFC_PROPERTIES);
        if (source.isPresent()) {
            expand(source.get().toAbsolutePath().normalize(), new HashSet<>(), lines);
        }

        Properties entries = new Properties();
        entries.setProperty(DOCBROKER_PREFIX + "host[0]", host);
        entries.setProperty(DOCBROKER_PREFIX + "port[0]", String.valueOf(port));
        entries.setProperty(DATA_DIR, dataDir.toString());
        ByteArrayOutputStream stored = new ByteArrayOutputStream();
        entries.store(stored, null);
        stored.toString(StandardCharsets.ISO_8859_1).lines()
                .filter(line -> !line.startsWith("#"))
                .forEach(lines::add);

        Files.write(configDir.resolve(DFC_PROPERTIES), lines, StandardCharsets.ISO_8859_1);
    }

    private void expand(Path file, Set<Path> chain, List<String> out) throws IOException {
        List<String> physicalLines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        chain.add(file);
        for (List<String> logicalLine : logicalLines(physicalLines)) {
            String first = logicalLine.get(0).strip();
            if (INCLUDE_LINE.matcher(first).matches()) {
                expandInclude(file, first.substring(INCLUDE.length()).strip(), chain, out);
            } else if (!isDroppedKey(logicalLine)) {
                out.addAll(logicalLine);
            }
        }
        chain.remove(file);
    }

    private void expandInclude(Path includingFile, String includePath, Set<Path> chain, List<String> out) {
        Path included;
        try {
            included = includingFile.resolveSibling(includePath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            log.warn("Skipped the include {} in {}: {}", includePath, includingFile, e.getMessage());
            return;
        }
        if (chain.contains(included)) {
            log.warn("Skipped the include {} in {}: the file is already being included", included, includingFile);
            return;
        }
        try {
            expand(included, chain, out);
        } catch (IOException e) {
            log.warn("Skipped the include {} in {}: the file cannot be read ({})", included, includingFile, e.toString());
        }
    }

    // Groups physical lines as java.util.Properties does: an odd number of trailing backslashes continues a line
    private static List<List<String>> logicalLines(List<String> physicalLines) {
        List<List<String>> logicalLines = new ArrayList<>();
        List<String> current = null;
        for (String line : physicalLines) {
            if (current == null) {
                current = new ArrayList<>();
                logicalLines.add(current);
            }
            boolean comment = current.isEmpty() && isCommentOrBlank(line);
            current.add(line);
            if (comment || !continues(line)) {
                current = null;
            }
        }
        return logicalLines;
    }

    private static boolean isCommentOrBlank(String line) {
        String text = line.stripLeading();
        return text.isEmpty() || text.startsWith("#") || text.startsWith("!");
    }

    private static boolean continues(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 != 0;
    }

    private static boolean isDroppedKey(List<String> logicalLine) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(String.join("\n", logicalLine)));
        for (String key : properties.stringPropertyNames()) {
            if (key.equals(DATA_DIR) || INDEXED_DOCBROKER_KEY.matcher(key).matches()) {
                return true;
            }
        }
        return false;
    }

    private void copyConfigFiles(Path configDir) throws IOException {
        if (dfcConfig.isBlank() || !Files.isDirectory(Path.of(dfcConfig))) {
            return;
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(Path.of(dfcConfig))) {
            files = listing
                    .filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().equals(DFC_PROPERTIES))
                    .toList();
        }
        for (Path file : files) {
            Files.copy(file, configDir.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
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
