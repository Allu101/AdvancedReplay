package me.jumper251.replay.commands.replay;

import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.api.ReplayAPI;
import me.jumper251.replay.commands.AbstractCommand;
import me.jumper251.replay.commands.SubCommand;
import me.jumper251.replay.filesystem.ConfigManager;
import me.jumper251.replay.filesystem.saving.ReplaySaver;
import me.jumper251.replay.utils.ReplayManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReplayStartCommand extends SubCommand {

	public ReplayStartCommand(AbstractCommand parent) {
		super(parent, "start", "Records a new replay", "start <Name> [<Players ...>] [Duration]", false);
	}

	@Override
	public boolean execute(CommandSender cs, Command cmd, String label, String[] args) {
		if(args.length < 2) {
			return false;
		}

		String name = args[1];

		if(name.length() > 40) {
			cs.sendMessage(ReplaySystem.PREFIX + "§cReplay name is too long.");
			return true;
		}

		if(ReplaySaver.exists(name) || ReplayManager.activeReplays.containsKey(name)) {
			cs.sendMessage(ReplaySystem.PREFIX + "§cReplay already exists.");
			return true;
		}

		List<String> argsList = new ArrayList<>(Arrays.asList(args.clone()));
		int duration;

		try {
			duration = Integer.parseInt(args[args.length-1]);
			argsList.remove(args.length-1);
		} catch (NumberFormatException nfe) {
			duration = ConfigManager.MAX_LENGTH;
		}

		if(argsList.size() == 2) {
			ReplayAPI.getInstance().recordReplay(name, cs, ConfigManager.MAX_LENGTH);

			sendStartedMsg(cs, ReplaySystem.PREFIX + "§aSuccessfully started recording §e" + name
					+ "§7.\n§7Use §6/Replay stop " + name + "§7 to save it."
					+ "\n§7INFO: You are recording all online players.");
			return true;
		}

		List<Player> playerList = new ArrayList<>();

		for (int i = 2; i < argsList.size(); i++) {
			Player player = Bukkit.getPlayer(args[i]);

			if(player != null) {
				playerList.add(player);
			}
		}

		Player[] players = new Player[playerList.size()];

		players = playerList.toArray(players);

		if(players.length > 0) {
			ReplayAPI.getInstance().recordReplay(name, cs, duration, players);

			sendStartedMsg(cs, ReplaySystem.PREFIX + "§aSuccessfully started recording §e" + name
					+ "§7.\n§7Use §6/Replay stop " + name + "§7 to save it.");
		}
		return true;
	}

	private void sendStartedMsg(CommandSender cs, String message) {
		cs.sendMessage(message);
	}
}