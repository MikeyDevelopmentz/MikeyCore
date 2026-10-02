package mikey.me.core.registry;

import mikey.me.core.persistence.DatabaseConfig;
import mikey.me.core.persistence.YamlFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class CoreRegistry {

    private static volatile Path root = Path.of("plugins", "MikeyCore");

    private CoreRegistry() {
    }

    public static void init(Path folder) {
        if (folder == null) {
            throw new IllegalArgumentException("folder");
        }
        root = folder;
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new IllegalStateException("couldnt make " + folder, e);
        }
    }

    public static Path root() {
        return root;
    }

    // plugins/MikeyCore/database.yml, not a per-plugin folder
    public static DatabaseConfig database(String poolName) throws IOException {
        return new PluginData("core", root).database(poolName);
    }

    // folder is plugins/MikeyCore/<id>
    public static PluginData register(String id) {
        if (id == null || !id.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("bad id");
        }
        Path folder = root.resolve(id);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new IllegalStateException("couldnt make " + folder, e);
        }
        return new PluginData(id, folder);
    }

    public static final class PluginData {
        private final String id;
        private final Path folder;

        private PluginData(String id, Path folder) {
            this.id = id;
            this.folder = folder;
        }

        public String id() {
            return id;
        }

        public Path folder() {
            return folder;
        }

        public Path file(String name) {
            if (name == null || name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
                throw new IllegalArgumentException("bad file");
            }
            return folder.resolve(name);
        }

        public void saveDefault(String name, InputStream resource) throws IOException {
            Path target = file(name);
            if (Files.exists(target) || resource == null) {
                return;
            }
            Files.createDirectories(folder);
            Files.copy(resource, target);
        }

        public void copyIfAbsent(String name, Path from) throws IOException {
            Path target = file(name);
            if (Files.exists(target) || from == null || !Files.exists(from)) {
                return;
            }
            Files.createDirectories(folder);
            Files.copy(from, target);
        }

        public DatabaseConfig database(String poolName) throws IOException {
            Path file = file("database.yml");
            if (!Files.exists(file)) {
                YamlFile.write(file, Map.of(
                        "host", "localhost",
                        "port", 3306,
                        "database", "mikey",
                        "user", "root",
                        "password", "",
                        "pool-size", 5,
                        "jdbc-url", ""));
            }
            Map<String, Object> map = YamlFile.read(file);
            String jdbc = YamlFile.string(map, "jdbc-url").trim();
            if (jdbc.isEmpty()) {
                jdbc = "jdbc:mysql://" + YamlFile.string(map, "host")
                        + ":" + YamlFile.integer(map, "port", 3306)
                        + "/" + YamlFile.string(map, "database")
                        + "?useSSL=false&characterEncoding=utf8";
            }
            int pool = YamlFile.integer(map, "pool-size", 5);
            return new DatabaseConfig(jdbc, YamlFile.string(map, "user"), YamlFile.string(map, "password"), pool, poolName);
        }
    }
}
