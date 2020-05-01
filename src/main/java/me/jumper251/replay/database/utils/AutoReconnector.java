package me.jumper251.replay.database.utils;


import me.jumper251.replay.database.DatabaseRegistry;
import me.jumper251.replay.database.MySQLDatabase;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class AutoReconnector extends BukkitRunnable {

    private Plugin plugin;

    public AutoReconnector(Plugin plugin) {
        this.plugin = plugin;

        this.runTaskTimerAsynchronously(plugin, 1200, 1200); // 60 secs
    }

    @Override
    public void run() {
        MySQLDatabase database = (MySQLDatabase) DatabaseRegistry.getDatabase();
        database.update("USE " + database.getDatabase() + "");
    }
}
