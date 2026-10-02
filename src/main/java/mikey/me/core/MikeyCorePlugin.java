package mikey.me.core;

import mikey.me.core.registry.CoreRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class MikeyCorePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        CoreRegistry.init(getDataFolder().toPath());
        getLogger().info("folder " + getDataFolder().getPath());
    }
}
