package mikey.me.core.registry;

import mikey.me.core.persistence.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreRegistryTest {

    @TempDir
    Path dir;

    @Test
    void registerMakesAFolderAndDatabaseFile() throws Exception {
        CoreRegistry.init(dir);
        CoreRegistry.PluginData data = CoreRegistry.register("holograms");
        assertEquals(dir.resolve("holograms"), data.folder());
        DatabaseConfig database = data.database("Holograms");
        assertTrue(Files.exists(data.file("database.yml")));
        assertEquals("Holograms", database.poolName());
        assertTrue(database.jdbcUrl().contains("localhost"));
    }

    @Test
    void rootDatabaseFileSitsNextToThePluginFolder() throws Exception {
        CoreRegistry.init(dir);
        DatabaseConfig database = CoreRegistry.database("MikeyCore");
        assertTrue(Files.exists(dir.resolve("database.yml")));
        assertEquals("MikeyCore", database.poolName());
        assertTrue(database.jdbcUrl().startsWith("jdbc:mysql://"));
    }

    @Test
    void rejectsABadId() {
        CoreRegistry.init(dir);
        assertThrows(IllegalArgumentException.class, () -> CoreRegistry.register("../nope"));
    }
}
