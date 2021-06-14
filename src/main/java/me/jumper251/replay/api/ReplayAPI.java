package me.jumper251.replay.api;

import me.jumper251.replay.filesystem.saving.IReplaySaver;
import me.jumper251.replay.filesystem.saving.ReplaySaver;
import me.jumper251.replay.replaysystem.Replay;
import me.jumper251.replay.replaysystem.replaying.ReplayHelper;
import me.jumper251.replay.replaysystem.replaying.Replayer;
import me.jumper251.replay.utils.ReplayManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReplayAPI {

	private static ReplayAPI instance;
	
	private HookManager hookManager;
	
	private ReplayAPI() {
		this.hookManager = new HookManager();
	}
	
	public void registerHook(IReplayHook hook) {
		this.hookManager.registerHook(hook);
	}
	
	public void unregisterHook(IReplayHook hook) {
		this.hookManager.unregisterHook(hook);
	}
	
	
	public Replay recordReplay(String name, CommandSender sender, int duration, Player... players) {
		List<Player> toRecord;
		
		if (players != null && players.length > 0) { 
			toRecord = Arrays.asList(players);
		} else {
			toRecord = new ArrayList<>(Bukkit.getOnlinePlayers());
		}
		
		Replay replay = new Replay();

		if (name != null) {
			replay.setId(name);
		}

		replay.recordAll(toRecord, sender, duration);
		
		return replay;
	}
	
	public void stopReplay(String name, boolean save) {
		if(!ReplayManager.activeReplays.containsKey(name)) {
			return;
		}

		Replay replay = ReplayManager.activeReplays.get(name);
		if (replay.isRecording()) {
			replay.getRecorder().stop(save);
		}
	}

	public void playReplay(String name, Player watcher) {
		if(!ReplaySaver.exists(name) || ReplayHelper.replaySessions.containsKey(watcher.getName())) {
			return;
		}

		ReplaySaver.load(name, replay -> replay.play(watcher));
	}


	public void jumpToReplayTime(Player watcher, Integer second) {
		if (ReplayHelper.replaySessions.containsKey(watcher.getName())) {
			Replayer replayer = ReplayHelper.replaySessions.get(watcher.getName());
			if (replayer != null) {
				int duration = replayer.getReplay().getData().getDuration() / 20;
				if (second > 0 && second <= duration) {
					replayer.getUtils().jumpTo(second);
				}
			}
		}
	}

	public void registerReplaySaver(IReplaySaver replaySaver) {
		ReplaySaver.register(replaySaver);
	}
	
	public HookManager getHookManager() {
		return hookManager;
	}
	
	
	public static ReplayAPI getInstance() {
		if (instance == null) instance = new ReplayAPI();
		
		return instance;
	}
}
