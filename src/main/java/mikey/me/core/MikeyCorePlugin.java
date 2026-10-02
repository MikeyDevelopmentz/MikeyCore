package mikey.me.core;

import mikey.me.core.persistence.DatabasePool;
import mikey.me.core.registry.CoreRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public final class MikeyCorePlugin extends JavaPlugin {

    private DatabasePool database;

    @Override
    public void onEnable() {
        CoreRegistry.init(getDataFolder().toPath());
        getLogger().info("folder " + getDataFolder().getPath());
        try {
            database = DatabasePool.open(CoreRegistry.database("MikeyCore"));
        } catch (IOException | RuntimeException e) {
            getLogger().warning("database didnt start: " + e.getMessage());
        }
    }

    public DatabasePool database() {
        return database;
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
