package com.spirecentral.dfcbridge.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DfcInstanceRegistryTest {

    @TempDir
    Path tempDir;

    private Path dfcHome;
    private Path dfcConfig;
    private Path instancesDir;
    private DfcInstanceRegistry registry;
    private Locale formatLocale;

    @BeforeEach
    void setUp() throws IOException {
        formatLocale = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.UK);
        dfcHome = Files.createDirectory(tempDir.resolve("shared"));
        dfcConfig = Files.createDirectory(tempDir.resolve("config"));
        instancesDir = tempDir.resolve("dfc-instances");
        for (String jar : List.of("dfc.jar", "aspectjrt.jar", "servlet-api.jar")) {
            Files.createFile(dfcHome.resolve(jar));
        }
        registry = new DfcInstanceRegistry(dfcHome.toString(), dfcConfig.toString(), instancesDir);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(Locale.Category.FORMAT, formatLocale);
    }

    @Test
    void loaderFor_writesDfcPropertiesWithOnlyTheRequestedDocbroker() throws IOException {
        Files.writeString(dfcConfig.resolve("dfc.properties"), String.join("\n",
                "dfc.docbroker.host[0]=192.168.0.110",
                "dfc.docbroker.port[0]=1489",
                "dfc.docbroker.host[1]=192.168.0.112",
                "dfc.docbroker.port[1]=1489",
                "dfc.data.dir=C\\:/Documentum",
                "dfc.globalregistry.repository=EDMS",
                "dfc.globalregistry.password=AAAAEA\\=\\=",
                "dfc.tracing.dir=C\\:/Documentum/logs",
                ""));

        registry.loaderFor("192.168.0.111", 1489);

        Properties generated = load(instancesDir.resolve("192.168.0.111_1489/config/dfc.properties"));
        Properties expected = new Properties();
        expected.setProperty("dfc.docbroker.host[0]", "192.168.0.111");
        expected.setProperty("dfc.docbroker.port[0]", "1489");
        expected.setProperty("dfc.data.dir", instancesDir.resolve("192.168.0.111_1489/data").toAbsolutePath().toString());
        expected.setProperty("dfc.globalregistry.repository", "EDMS");
        expected.setProperty("dfc.globalregistry.password", "AAAAEA==");
        expected.setProperty("dfc.tracing.dir", "C:/Documentum/logs");
        expected.setProperty("dfc.date_format", "dd/MM/yyyy HH:mm:ss");
        assertEquals(expected, generated);
        assertTrue(Files.isDirectory(instancesDir.resolve("192.168.0.111_1489/data")));
    }

    @Test
    void loaderFor_noDfcPropertiesInDfcConfig_writesOnlyTheDocbrokerDataDirAndDateFormat() throws IOException {
        registry.loaderFor("cs23", 1489);

        Properties generated = load(instancesDir.resolve("cs23_1489/config/dfc.properties"));
        assertEquals(Set.of("dfc.docbroker.host[0]", "dfc.docbroker.port[0]", "dfc.data.dir", "dfc.date_format"),
                generated.stringPropertyNames());
        assertEquals("cs23", generated.getProperty("dfc.docbroker.host[0]"));
        assertEquals("dd/MM/yyyy HH:mm:ss", generated.getProperty("dfc.date_format"));
    }

    @Test
    void loaderFor_noDateFormatInDfcProperties_writesTheLocaleDateFormat() throws IOException {
        writeConfig("dfc.properties", "dfc.globalregistry.repository=EDMS");

        registry.loaderFor("cs23", 1489);

        assertEquals("dd/MM/yyyy HH:mm:ss", generatedProperties().getProperty("dfc.date_format"));
    }

    @Test
    void loaderFor_dateFormatInDfcProperties_isKeptAndNoOtherIsWritten() throws IOException {
        writeConfig("dfc.properties", "dfc.date_format = yyyy-MM-dd HH:mm:ss");

        registry.loaderFor("cs23", 1489);

        assertEquals(1, generatedLines().stream().filter(line -> line.startsWith("dfc.date_format")).count());
        assertEquals("yyyy-MM-dd HH:mm:ss", generatedProperties().getProperty("dfc.date_format"));
    }

    @Test
    void loaderFor_blankDateFormatInDfcProperties_writesTheLocaleDateFormat() throws IOException {
        writeConfig("dfc.properties", "dfc.date_format=");

        registry.loaderFor("cs23", 1489);

        assertEquals("dd/MM/yyyy HH:mm:ss", generatedProperties().getProperty("dfc.date_format"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "dd/MM/y, HH:mm:ss | dd/MM/yyyy HH:mm:ss",
            "M/d/yy, h:mm:ss a | M/d/yyyy h:mm:ss a",
            "d/MM/yy HH:mm:ss | d/MM/yyyy HH:mm:ss",
            "M/d/yy, h:mm:ss\u202Fa | M/d/yyyy h:mm:ss a",
            "yyyy-MM-dd HH:mm:ss | yyyy-MM-dd HH:mm:ss"
    })
    void dateFormatFrom_givesAFourDigitYearAndPlainSpacesWithNoComma(String pattern, String expected) {
        assertEquals(expected, DfcInstanceRegistry.dateFormatFrom(pattern));
    }

    @Test
    void loaderFor_copiesTheKeystoreIntoTheInstanceConfig() throws IOException {
        Files.write(dfcConfig.resolve("dfc.keystore"), new byte[]{1, 2, 3});

        registry.loaderFor("cs23", 1489);

        assertArrayEquals(new byte[]{1, 2, 3},
                Files.readAllBytes(instancesDir.resolve("cs23_1489/config/dfc.keystore")));
    }

    @Test
    void loaderFor_copiesTheLog4jConfigurationIntoTheInstanceConfig() throws IOException {
        Files.writeString(dfcConfig.resolve("log4j2.properties"), "rootLogger.level=info\n");

        registry.loaderFor("cs23", 1489);

        assertEquals("rootLogger.level=info\n",
                Files.readString(instancesDir.resolve("cs23_1489/config/log4j2.properties")));
    }

    @Test
    void loaderFor_sameHostAndPort_returnsTheSameLoader() {
        assertSame(registry.loaderFor("cs23", 1489), registry.loaderFor("cs23", 1489));
    }

    @Test
    void loaderFor_otherPortOrOtherHost_returnsAnotherLoader() {
        ClassLoader loader = registry.loaderFor("cs23", 1489);

        assertNotSame(loader, registry.loaderFor("cs23", 1490));
        assertNotSame(loader, registry.loaderFor("192.168.0.110", 1489));
    }

    @Test
    void loaderFor_parallelFirstCalls_returnOneLoader() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ClassLoader>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return registry.loaderFor("cs23", 1489);
                }));
            }
            start.countDown();

            Set<ClassLoader> loaders = new HashSet<>();
            for (Future<ClassLoader> result : results) {
                loaders.add(result.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, loaders.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void loaderFor_urlsAreTheConfigFolderThenTheDfcJarsWithoutServletApi() throws Exception {
        URLClassLoader loader = (URLClassLoader) registry.loaderFor("cs23", 1489);

        List<URL> expected = List.of(
                instancesDir.resolve("cs23_1489/config").toAbsolutePath().toUri().toURL(),
                dfcHome.resolve("aspectjrt.jar").toAbsolutePath().toUri().toURL(),
                dfcHome.resolve("dfc.jar").toAbsolutePath().toUri().toURL());
        assertEquals(expected, List.of(loader.getURLs()));
        assertSame(ClassLoader.getPlatformClassLoader(), loader.getParent());
    }

    @Test
    void loaderFor_hostWithOtherCharacters_replacesThemInTheFolderName() {
        registry.loaderFor("cs23:tst/a b", 1489);

        assertTrue(Files.isRegularFile(instancesDir.resolve("cs23_tst_a_b_1489/config/dfc.properties")));
    }

    @Test
    void loaderFor_keepsGlobalDocbrokerSettingsAndDropsIndexedEntries() throws IOException {
        writeConfig("dfc.properties",
                "dfc.docbroker.search_order=sequential",
                "dfc.docbroker.exclusion.time=30",
                "dfc.docbroker.host[0]=192.168.0.110",
                "dfc.docbroker.port[0]=1489",
                "dfc.docbroker.timeout[0]=10");

        registry.loaderFor("cs23", 1489);

        Properties generated = generatedProperties();
        assertEquals("sequential", generated.getProperty("dfc.docbroker.search_order"));
        assertEquals("30", generated.getProperty("dfc.docbroker.exclusion.time"));
        assertNull(generated.getProperty("dfc.docbroker.timeout[0]"));
        assertEquals("cs23", generated.getProperty("dfc.docbroker.host[0]"));
    }

    @Test
    void loaderFor_keyWithColonOrSpacesAroundEquals_isRecognised() throws IOException {
        writeConfig("dfc.properties",
                "  dfc.docbroker.host[1]:192.168.0.112",
                "dfc.data.dir = /opt/documentum",
                "dfc.docbroker.port[1]    1489");

        registry.loaderFor("cs23", 1489);

        List<String> lines = generatedLines();
        assertFalse(lines.contains("  dfc.docbroker.host[1]:192.168.0.112"));
        assertFalse(lines.contains("dfc.data.dir = /opt/documentum"));
        assertFalse(lines.contains("dfc.docbroker.port[1]    1489"));
    }

    @Test
    void loaderFor_continuedLineOfADroppedEntry_isDroppedWithIt() throws IOException {
        writeConfig("dfc.properties",
                "dfc.docbroker.host[1]=192.168.\\",
                "    0.112",
                "dfc.globalregistry.repository=EDMS");

        registry.loaderFor("cs23", 1489);

        assertFalse(generatedLines().contains("    0.112"));
        assertEquals("EDMS", generatedProperties().getProperty("dfc.globalregistry.repository"));
    }

    @Test
    void loaderFor_relativeInclude_expandsTheFileWithoutItsDocbrokerOrDataDir() throws IOException {
        writeConfig("shared.properties",
                "dfc.date_format=dd/MM/yyyy HH:mm:ss",
                "dfc.docbroker.host[1]=192.168.0.111",
                "dfc.data.dir=/opt/documentum");
        writeConfig("dfc.properties",
                "#include shared.properties",
                "dfc.globalregistry.repository=EDMS");

        registry.loaderFor("cs23", 1489);

        Properties generated = generatedProperties();
        assertEquals("dd/MM/yyyy HH:mm:ss", generated.getProperty("dfc.date_format"));
        assertEquals("EDMS", generated.getProperty("dfc.globalregistry.repository"));
        assertNull(generated.getProperty("dfc.docbroker.host[1]"));
        assertNotEquals("/opt/documentum", generated.getProperty("dfc.data.dir"));
        assertTrue(generatedLines().stream().noneMatch(line -> line.startsWith("#include")));
    }

    @Test
    void loaderFor_absoluteIncludeOutsideDfcConfigAndNestedInclude_areExpanded() throws IOException {
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("nested.properties"), "dfc.tracing.enable=true\n");
        Files.writeString(outside.resolve("shared.properties"),
                "#include nested.properties\ndfc.date_format=dd/MM/yyyy\n");
        writeConfig("dfc.properties", "#include " + outside.resolve("shared.properties").toAbsolutePath());

        registry.loaderFor("cs23", 1489);

        Properties generated = generatedProperties();
        assertEquals("dd/MM/yyyy", generated.getProperty("dfc.date_format"));
        assertEquals("true", generated.getProperty("dfc.tracing.enable"));
    }

    @Test
    void loaderFor_includedFileEndingInABackslash_doesNotContinueIntoTheIncludingFile() throws IOException {
        writeConfig("common.properties", "dfc.tokenstorage.dir=C\\:/apptoken\\");
        writeConfig("dfc.properties",
                "#include common.properties",
                "dfc.globalregistry.repository=EDMS");

        registry.loaderFor("cs23", 1489);

        Properties generated = generatedProperties();
        assertEquals("C:/apptoken", generated.getProperty("dfc.tokenstorage.dir"));
        assertEquals("EDMS", generated.getProperty("dfc.globalregistry.repository"));
    }

    @Test
    void loaderFor_sourceFileEndingInABackslash_doesNotContinueIntoTheAppendedEntries() throws IOException {
        writeConfig("dfc.properties", "dfc.tokenstorage.dir=C\\:/apptoken\\");

        registry.loaderFor("cs23", 1489);

        Properties generated = generatedProperties();
        assertEquals("C:/apptoken", generated.getProperty("dfc.tokenstorage.dir"));
        assertEquals(instancesDir.resolve("cs23_1489/data").toAbsolutePath().toString(),
                generated.getProperty("dfc.data.dir"));
    }

    @Test
    void loaderFor_missingIncludeFile_isSkippedAndTheFileIsWritten() throws IOException {
        writeConfig("dfc.properties",
                "#include no-such-file.properties",
                "dfc.globalregistry.repository=EDMS");

        registry.loaderFor("cs23", 1489);

        Properties generated = generatedProperties();
        assertEquals("EDMS", generated.getProperty("dfc.globalregistry.repository"));
        assertEquals("cs23", generated.getProperty("dfc.docbroker.host[0]"));
    }

    @Test
    void loaderFor_fileThatIncludesItself_isExpandedOnce() throws IOException {
        writeConfig("dfc.properties",
                "#include dfc.properties",
                "dfc.globalregistry.repository=EDMS");

        registry.loaderFor("cs23", 1489);

        assertEquals(1, generatedLines().stream()
                .filter(line -> line.equals("dfc.globalregistry.repository=EDMS"))
                .count());
    }

    @Test
    void loaderFor_commentsAndEscapedValues_areCopiedUnchanged() throws IOException {
        writeConfig("dfc.properties",
                "# Documentum settings",
                "",
                "! tracing",
                "dfc.tracing.dir=C\\:/Documentum/logs");

        registry.loaderFor("cs23", 1489);

        assertEquals(List.of("# Documentum settings", "", "! tracing", "dfc.tracing.dir=C\\:/Documentum/logs"),
                generatedLines().subList(0, 4));
    }

    @Test
    void loaderFor_generatedFile_namesOnlyTheRequestedDocbrokerAndTheInstanceDataDir() throws IOException {
        writeConfig("dfc.properties",
                "dfc.docbroker.host[0]=192.168.0.110",
                "dfc.docbroker.port[0]=1489",
                "dfc.data.dir=C\\:/Documentum");

        registry.loaderFor("cs23", 1490);

        Properties generated = load(instancesDir.resolve("cs23_1490/config/dfc.properties"));
        Set<String> docbrokerKeys = new HashSet<>();
        for (String key : generated.stringPropertyNames()) {
            if (key.startsWith("dfc.docbroker.")) {
                docbrokerKeys.add(key);
            }
        }
        assertEquals(Set.of("dfc.docbroker.host[0]", "dfc.docbroker.port[0]"), docbrokerKeys);
        assertEquals("cs23", generated.getProperty("dfc.docbroker.host[0]"));
        assertEquals("1490", generated.getProperty("dfc.docbroker.port[0]"));
        assertEquals(instancesDir.resolve("cs23_1490/data").toAbsolutePath().toString(),
                generated.getProperty("dfc.data.dir"));
    }

    private void writeConfig(String fileName, String... lines) throws IOException {
        Files.writeString(dfcConfig.resolve(fileName), String.join("\n", lines) + "\n");
    }

    private Properties generatedProperties() throws IOException {
        return load(instancesDir.resolve("cs23_1489/config/dfc.properties"));
    }

    private List<String> generatedLines() throws IOException {
        return Files.readAllLines(instancesDir.resolve("cs23_1489/config/dfc.properties"), StandardCharsets.ISO_8859_1);
    }

    private static Properties load(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        }
        return properties;
    }
}
