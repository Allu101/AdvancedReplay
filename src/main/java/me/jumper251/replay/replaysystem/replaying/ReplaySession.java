package me.jumper251.replay.replaysystem.replaying;

import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.filesystem.ConfigManager;
import me.jumper251.replay.filesystem.ItemConfig;
import me.jumper251.replay.filesystem.ItemConfigOption;
import me.jumper251.replay.filesystem.ItemConfigType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class ReplaySession {

	private boolean allowFlight;
	private boolean flying;
	private int level;
	private float xp;

	private ItemStack content[];
	private Location start;
	private Player player;

	private Replayer replayer;
	private ReplayPacketListener packetListener;
	
	public ReplaySession(Replayer replayer) {
		this.replayer = replayer;
		
		player = this.replayer.getWatchingPlayer();
		packetListener = new ReplayPacketListener(replayer);
	}
	
	public void startSession() {
		content = player.getInventory().getContents();
		start = player.getLocation();

		level = player.getLevel();
		xp = player.getExp();
		allowFlight = player.getAllowFlight();
		flying = player.isFlying();

		new BukkitRunnable() {
			@Override
			public void run() {
				player.setHealth(20);
				player.setFoodLevel(20);
				player.getInventory().clear();

				List<ItemConfigOption> configItems = new ArrayList<>();

				configItems.add(ItemConfig.getItem(ItemConfigType.TELEPORT));
				configItems.add(ItemConfig.getItem(ItemConfigType.SPEED));
				configItems.add(ItemConfig.getItem(ItemConfigType.LEAVE));
				configItems.add(ItemConfig.getItem(ItemConfigType.BACKWARD));
				configItems.add(ItemConfig.getItem(ItemConfigType.FORWARD));
				configItems.add(ItemConfig.getItem(ItemConfigType.PAUSE));

				configItems.stream().forEach(item -> player.getInventory().setItem(item.getSlot(), ReplayHelper.createItem(item)));

				player.setAllowFlight(true);
				player.setFlying(true);

				if (ConfigManager.HIDE_PLAYERS) {
					for (Player p : Bukkit.getOnlinePlayers()) {
						if (p != player) {
							player.hidePlayer(p);
						}
					}
				}
			}
		}.runTask(ReplaySystem.getInstance());

	}
	
	public void stopSession() {
		ReplayHelper.replaySessions.remove(player.getName());
		
		packetListener.unregister();
		player.getInventory().clear();
		if (player == null) {
			return;
		}
		
		new BukkitRunnable() {
			@Override
			public void run() {
				player.getInventory().setContents(content);

				player.setLevel(level);
				player.setExp(xp);
				player.setAllowFlight(allowFlight);
				player.setFlying(flying);
				
				player.teleport(start);
				
				if (ConfigManager.HIDE_PLAYERS) {
					for (Player p : Bukkit.getOnlinePlayers()) {
						if (p != player) {
							player.showPlayer(p);
						}
					}
				}
				
			}
		}.runTask(ReplaySystem.getInstance());
	}
	
	public ReplayPacketListener getPacketListener() {
		return packetListener;
	}

}
