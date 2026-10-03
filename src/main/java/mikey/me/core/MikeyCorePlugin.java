package mikey.me.core;

import mikey.me.core.persistence.DatabasePool;
import mikey.me.core.registry.CoreRegistry;
import mikey.me.core.web.WebApps;
import mikey.me.core.web.WebServer;
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
        try {
            WebServer web = WebApps.open(CoreRegistry.root().resolve("web.yml"));
            if (web != null) {
                if (web.localOnly()) {
                    getLogger().info("web on " + web.host() + ":" + web.port() + " localhost only");
                } else {
                    getLogger().info("web on port " + web.port());
                }
            }
        } catch (IOException | RuntimeException e) {
            getLogger().warning("web didnt start: " + e.getMessage());
        }
    }

    public DatabasePool database() {
        return database;
    }

    @Override
    public void onDisable() {
        WebApps.stop();
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
