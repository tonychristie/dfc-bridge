package com.spirecentral.dfcbridge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    @BeforeEach
    void setUp() throws IOException {
        dfcHome = Files.createDirectory(tempDir.resolve("shared"));
        dfcConfig = Files.createDirectory(tempDir.resolve("config"));
        instancesDir = tempDir.resolve("dfc-instances");
        for (String jar : List.of("dfc.jar", "aspectjrt.jar", "servlet-api.jar")) {
            Files.createFile(dfcHome.resolve(jar));
        }
        registry = new DfcInstanceRegistry(dfcHome.toString(), dfcConfig.toString(), instancesDir);
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
        assertEquals(expected, generated);
        assertTrue(Files.isDirectory(instancesDir.resolve("192.168.0.111_1489/data")));
    }

    @Test
    void loaderFor_noDfcPropertiesInDfcConfig_writesOnlyTheDocbrokerAndDataDir() throws IOException {
        registry.loaderFor("cs23", 1489);

        Properties generated = load(instancesDir.resolve("cs23_1489/config/dfc.properties"));
        assertEquals(Set.of("dfc.docbroker.host[0]", "dfc.docbroker.port[0]", "dfc.data.dir"),
                generated.stringPropertyNames());
        assertEquals("cs23", generated.getProperty("dfc.docbroker.host[0]"));
    }

    @Test
    void loaderFor_copiesTheKeystoreIntoTheInstanceConfig() throws IOException {
        Files.write(dfcConfig.resolve("dfc.keystore"), new byte[]{1, 2, 3});

        registry.loaderFor("cs23", 1489);

        assertArrayEquals(new byte[]{1, 2, 3},
                Files.readAllBytes(instancesDir.resolve("cs23_1489/config/dfc.keystore")));
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

    private static Properties load(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        }
        return properties;
    }
}
