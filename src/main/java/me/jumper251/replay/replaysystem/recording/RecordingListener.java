package me.jumper251.replay.replaysystem.recording;

import me.jumper251.replay.filesystem.ConfigManager;
import me.jumper251.replay.listener.AbstractListener;
import me.jumper251.replay.replaysystem.data.ActionData;
import me.jumper251.replay.replaysystem.data.ActionType;
import me.jumper251.replay.replaysystem.data.types.*;
import me.jumper251.replay.replaysystem.utils.ItemUtils;
import me.jumper251.replay.replaysystem.utils.NPCManager;
import me.jumper251.replay.utils.MaterialBridge;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RecordingListener extends AbstractListener {

	private PacketRecorder packetRecorder;
	private Recorder recorder;

	private List<String> replayLeft;
	
	public RecordingListener(PacketRecorder packetRecorder) {
		this.packetRecorder = packetRecorder;
		this.recorder = this.packetRecorder.getRecorder();
		this.replayLeft = new ArrayList<>();
	}
	
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onClick(InventoryClickEvent e) {
		if (e.getWhoClicked() instanceof Player) {
			Player p = (Player) e.getWhoClicked();
			if (recorder.getPlayers().contains(p.getName())) {
				this.packetRecorder.addData(p.getName(), NPCManager.copyFromPlayer(p, true, true));
			}
		}
	}
	
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onHeld(PlayerItemHeldEvent e) {
		Player p = e.getPlayer();
		if (recorder.getPlayers().contains(p.getName())) {
			ItemStack stack = p.getInventory().getItem(e.getNewSlot());
			itemInHand(p, stack);
		}
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler 
	public void onInteract(PlayerInteractEvent e) {
		Player p = e.getPlayer();
		if (recorder.getPlayers().contains(p.getName())) {
			if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
				
				boolean isInteractable = e.getClickedBlock() != null && ItemUtils.isInteractable(e.getClickedBlock().getType());
				if(e.getItem() != null && ItemUtils.isUsable(e.getItem().getType()) && (!isInteractable || p.isSneaking())) {
					PlayerWatcher watcher = this.recorder.getData().getWatcher(p.getName());
					if (!watcher.isBlocking()) {
						watcher.setBlocking(true);
						this.packetRecorder.addData(p.getName(), new MetadataUpdate(watcher.isBurning(), true));
					}
				}
				
				if (NPCManager.isArmor(e.getItem()) && !NPCManager.wearsArmor(p, NPCManager.getArmorType(e.getItem()))) {
					InvData data = NPCManager.copyFromPlayer(p, true, true);
					String armorType = NPCManager.getArmorType(e.getItem());
					if (armorType != null) {
						if (armorType.equals("head")) data.setHead(NPCManager.fromItemStack(e.getItem()));
						if (armorType.equals("chest")) data.setChest(NPCManager.fromItemStack(e.getItem()));
						if (armorType.equals("leg")) data.setLeg(NPCManager.fromItemStack(e.getItem()));
						if (armorType.equals("boots")) data.setBoots(NPCManager.fromItemStack(e.getItem()));
					}
					data.setMainHand(null);
					
					this.packetRecorder.addData(p.getName(), data);
				}
			}

			if (e.getAction() == Action.LEFT_CLICK_BLOCK && p.getTargetBlock(null, 5).getType() == Material.FIRE) {
				LocationData location = LocationData.fromLocation(p.getTargetBlock(null, 5).getLocation());

				ItemData before = new ItemData(Material.FIRE.getId(), 0);
				ItemData after = new ItemData(0, 0);

				this.packetRecorder.addData(p.getName(), new BlockChangeData(location, before, after));
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler
	public void onConsume(PlayerItemConsumeEvent e) {
		Player p = e.getPlayer();
		if (recorder.getPlayers().contains(p.getName())) {
			PlayerWatcher watcher = recorder.getData().getWatcher(p.getName());
			if (watcher.isBlocking()) {
   				watcher.setBlocking(false);
   				this.packetRecorder.addData(p.getName(), new MetadataUpdate(watcher.isBurning(), false));
   				
				InvData data = NPCManager.copyFromPlayer(p, true, true);
				if (p.getItemInHand() != null && p.getItemInHand().getAmount() <= 1) {
					data.setMainHand(null);
				}
				this.packetRecorder.addData(p.getName(), data);
   			}
		}
	}
	
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player) {
			Player p = (Player)e.getEntity();
			if (recorder.getPlayers().contains(p.getName())) {
				this.packetRecorder.addData(p.getName(), new AnimationData(1));

				PlayerWatcher watcher = this.recorder.getData().getWatcher(p.getName());
				if (p.getFireTicks() > 20 && !watcher.isBurning()) {
					watcher.setBurning(true);
					this.packetRecorder.addData(p.getName(), new MetadataUpdate(true, watcher.isBlocking()));
				} else if (p.getFireTicks() <= 20 && watcher.isBurning()){
					watcher.setBurning(false);
					this.packetRecorder.addData(p.getName(), new MetadataUpdate(false, watcher.isBlocking()));
				}
			}
		} else if (e.getEntity() instanceof LivingEntity) {
			LivingEntity living = (LivingEntity) e.getEntity();
			if (this.packetRecorder.getEntityLookup().containsKey(living.getEntityId())) {
				this.packetRecorder.addData(this.packetRecorder.getEntityLookup().get(living.getEntityId()), new EntityAnimationData(living.getEntityId(), (living.getHealth() - e.getFinalDamage()) > 0 ? 2 : 3));
			}
		}
	}
	
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onCrit(EntityDamageByEntityEvent e) {
		if (e.getEntity() instanceof Player && e.getDamager() instanceof Player) {
			Player damager = (Player) e.getDamager();
			String victimName = e.getEntity().getName();
			if (recorder.getPlayers().contains(damager.getName()) && recorder.getPlayers().contains(victimName) &&
					damager.getFallDistance() > 0.0F && !damager.isOnGround() && damager.getVehicle() == null) {
				this.packetRecorder.addData(victimName, new AnimationData(4));
			}
		}
	}

	@EventHandler
	public void onChat(AsyncPlayerChatEvent e) {
		String name = e.getPlayer().getName();
		if (recorder.getPlayers().contains(name)) {
			this.packetRecorder.addData(name, new ChatData(e.getMessage()));
		}
	}

	@EventHandler
	public void onGamemodeChange(PlayerGameModeChangeEvent e) {
		String name = e.getPlayer().getName();
		if (recorder.getPlayers().contains(name)) {
			this.packetRecorder.addData(name, new ChatData(e.getNewGameMode().name()));
		}
	}

	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onBed(PlayerBedEnterEvent e) {
		Player p = e.getPlayer();
		if (recorder.getPlayers().contains(p.getName())) {
			this.packetRecorder.addData(p.getName(), new BedEnterData(LocationData.fromLocation(e.getBed().getLocation())));
		}
	}
	
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onBedLeave(PlayerBedLeaveEvent e) {
		Player p = e.getPlayer();
		if (recorder.getPlayers().contains(p.getName())) {
			this.packetRecorder.addData(p.getName(), new AnimationData(2));
		}
	}
	
	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		String name = e.getPlayer().getName();
		if (this.recorder.getPlayers().contains(name)) {
			this.recorder.addData(this.recorder.getCurrentTick(), new ActionData(0, ActionType.DESPAWN, name, null));
			this.recorder.getPlayers().remove(name);
		}
	}
	
	@EventHandler
	public void onDeath(PlayerDeathEvent e) {
		Player p = e.getEntity();
		if (this.recorder.getPlayers().contains(p.getName())) {
			this.recorder.addData(this.recorder.getCurrentTick(), new ActionData(0, ActionType.DEATH, p.getName(), null));

			if (!this.replayLeft.contains(p.getName())) this.replayLeft.add(p.getName());
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		String name = p.getName();
		if (!this.recorder.getPlayers().contains(name) && (this.replayLeft.contains(name)) || ConfigManager.ADD_PLAYERS) {
			this.recorder.getPlayers().add(name);
			this.recorder.getData().getWatchers().put(name, new PlayerWatcher(name));
			this.recorder.createSpawnAction(p, p.getLocation(), false);
		}
	}
	
	@EventHandler (priority = EventPriority.MONITOR)
	public void onRespawn(PlayerRespawnEvent e) {
		Player p = e.getPlayer();
		if (this.recorder.getPlayers().contains(p.getName())) {
			this.recorder.createSpawnAction(p, e.getRespawnLocation(), false);
		}
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler
	public void onThrow(PlayerDropItemEvent e) {
		Player p = e.getPlayer();
		if (this.recorder.getPlayers().contains(p.getName())) {
			InvData data = NPCManager.copyFromPlayer(p, true, true);
			if (data.getMainHand() != null && p.getItemInHand() != null && p.getItemInHand().getAmount() <= 1 && p.getItemInHand().getType() == MaterialBridge.fromID(data.getMainHand().getId())) {
				data.setMainHand(null);
			}
			this.packetRecorder.addData(p.getName(), data);
		}
	}
	
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onLaunch(ProjectileLaunchEvent e) {
		Projectile proj = e.getEntity();
		if (proj.getShooter() instanceof Player) {
			Player p = (Player)proj.getShooter();
			if (this.recorder.getPlayers().contains(p.getName())) {
				LocationData spawn = LocationData.fromLocation(p.getEyeLocation());
				LocationData velocity = LocationData.fromLocation(proj.getVelocity().toLocation(p.getWorld()));
				
				this.packetRecorder.addData(p.getName(), new ProjectileData(spawn, velocity, proj.getType()));
				this.packetRecorder.addData(p.getName(), NPCManager.copyFromPlayer(p, true, true));
			}
		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onPickup(PlayerPickupItemEvent e) {
		Player p = e.getPlayer();
		if (this.recorder.getPlayers().contains(p.getName())) {
			// Change PlayerItemInHand
			itemInHand(p, e.getItem().getItemStack());
		}
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onPlace(BlockPlaceEvent e) {
		Player p = e.getPlayer();
		if (this.recorder.getPlayers().contains(p.getName())) {
			LocationData location = LocationData.fromLocation(e.getBlockPlaced().getLocation());
			
			ItemData before = new ItemData(e.getBlockReplacedState().getType().getId(), e.getBlockReplacedState().getData().getData());
			ItemData after = new ItemData(e.getBlockPlaced().getType().getId(), e.getBlockPlaced().getData());
			
			this.packetRecorder.addData(p.getName(), new BlockChangeData(location, before, after));

			// Change PlayerItemInHand when last block in hand
			if (e.getItemInHand().getAmount() == 1) {
				ItemStack stack = new ItemStack(Material.AIR, 1);
				itemInHand(p, stack);
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onBreak(BlockBreakEvent e) {
		Player p = e.getPlayer();
		if (this.recorder.getPlayers().contains(p.getName())) {
			LocationData location = LocationData.fromLocation(e.getBlock().getLocation());

			ItemData before = new ItemData(e.getBlock().getType().getId(), e.getBlock().getData());
			ItemData after = new ItemData(0, 0);
			
			this.packetRecorder.addData(p.getName(), new BlockChangeData(location, before, after));
		}
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onFill(PlayerBucketFillEvent e) {
		Player p = e.getPlayer();
		if (this.recorder.getPlayers().contains(p.getName())) {
			LocationData location = LocationData.fromLocation(e.getBlockClicked().getLocation());

			ItemData before = new ItemData(e.getBlockClicked().getState().getType().getId(), e.getBlockClicked().getState().getData().getData());
			ItemData after = new ItemData(0, 0);
			
			this.packetRecorder.addData(p.getName(), new BlockChangeData(location, before, after));

			// Change PlayerItemInHand when fill bucket
			ItemStack stack;
			if (e.getBlockClicked().getState().getType().getId() == 10 || e.getBlockClicked().getState().getType().getId() == 11) {
				stack = new ItemStack(Material.LAVA_BUCKET, 1);
			} else {
				stack = new ItemStack(Material.WATER_BUCKET, 1);
			}
			itemInHand(p, stack);
		}
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onEmpty(PlayerBucketEmptyEvent e) {
		Player p = e.getPlayer();
		if (this.recorder.getPlayers().contains(p.getName())) {
			Block block = e.getBlockClicked().getRelative(e.getBlockFace());
			LocationData location = LocationData.fromLocation(block.getLocation());
			
			ItemData before = new ItemData(block.getType().getId(), block.getData());
			ItemData after = new ItemData(e.getBucket() == Material.WATER_BUCKET ? 9 : 11, 0);
			
			this.packetRecorder.addData(p.getName(), new BlockChangeData(location, before, after));

			// Change PlayerItemInHand
			ItemStack stack = new ItemStack(Material.BUCKET, 1);
			itemInHand(p, stack);
		}
	}
	
	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent e) {
		Player p = e.getPlayer();
		if (this.recorder.getPlayers().contains(p.getName())) {
			LocationData location = LocationData.fromLocation(p.getLocation());
			this.packetRecorder.addData(p.getName(), new WorldChangeData(location));
		}
	}

	public void itemInHand(Player p, ItemStack stack) {
		InvData data = NPCManager.copyFromPlayer(p, true, true);
		data.setMainHand(NPCManager.fromItemStack(stack));

		this.packetRecorder.addData(p.getName(), data);

		PlayerWatcher watcher = recorder.getData().getWatcher(p.getName());
		if (watcher.isBlocking()) {
			watcher.setBlocking(false);
			this.packetRecorder.addData(p.getName(), new MetadataUpdate(watcher.isBurning(), false));
		}
	}
}
