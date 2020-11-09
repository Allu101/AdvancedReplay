package me.jumper251.replay.replaysystem.replaying;


import com.comphenix.packetwrapper.AbstractPacket;
import com.comphenix.packetwrapper.WrapperPlayServerEntityDestroy;
import com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment;
import com.comphenix.packetwrapper.WrapperPlayServerEntityVelocity;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerAction;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.filesystem.ConfigManager;
import me.jumper251.replay.filesystem.MessageBuilder;
import me.jumper251.replay.replaysystem.data.ActionData;
import me.jumper251.replay.replaysystem.data.ActionType;
import me.jumper251.replay.replaysystem.data.ReplayData;
import me.jumper251.replay.replaysystem.data.types.*;
import me.jumper251.replay.replaysystem.recording.PlayerWatcher;
import me.jumper251.replay.replaysystem.utils.MetadataBuilder;
import me.jumper251.replay.replaysystem.utils.NPCManager;
import me.jumper251.replay.replaysystem.utils.entities.*;
import me.jumper251.replay.utils.MaterialBridge;
import me.jumper251.replay.utils.MathUtils;
import me.jumper251.replay.utils.VersionUtil;
import me.jumper251.replay.utils.VersionUtil.VersionEnum;
import org.bson.Document;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ReplayingUtils {

	private Replayer replayer;
	
	private Deque<ActionData> lastSpawnActions;
	
	private HashMap<Integer, Entity> itemEntities;

	private Map<String, SignatureData> signatures;

	private HashMap<Integer, Integer> hooks;

	public ReplayingUtils(Replayer replayer) {
		this.replayer = replayer;
		this.itemEntities = new HashMap<Integer, Entity>();
		this.hooks = new HashMap<Integer, Integer>();

		this.lastSpawnActions = new ArrayDeque<>();
		this.signatures = new HashMap<>();
	}
	
	public void handleAction(ActionData action, ReplayData data, boolean reversed) {
		if (action.getType() == ActionType.SPAWN) {
			if (!reversed) {
				spawnNPC(action);
			} else if (reversed && replayer.getNPCList().containsKey(getName(action.getName()))){
				INPC npc = this.replayer.getNPCList().get(getName(action.getName()));
				npc.remove();
				replayer.getNPCList().remove(getName(action.getName()));

			}
		}

		if (action.getType() == ActionType.PACKET && this.replayer.getNPCList().containsKey(getName(action.getName()))) {
			INPC npc = this.replayer.getNPCList().get(getName(action.getName()));

			PacketData packetData = action.getPacketData();
			if (packetData instanceof MovingData) {
				MovingData movingData = (MovingData) packetData;

				if (VersionUtil.isAbove(VersionEnum.V1_15) || VersionUtil.isCompatible(VersionEnum.V1_8)) {
					npc.move(new Location(replayer.getWatchingPlayer().getWorld(), movingData.getX(), movingData.getY(), movingData.getZ()), true, movingData.getYaw(), movingData.getPitch());
				}

				if (VersionUtil.isBetween(VersionEnum.V1_9, VersionEnum.V1_14)) {
					npc.teleport(new Location(replayer.getWatchingPlayer().getWorld(), movingData.getX(), movingData.getY(), movingData.getZ()), true);
					npc.look(movingData.getYaw(), movingData.getPitch());
				}
			}
			else if (packetData instanceof EntityActionData) {
				EntityActionData eaData = (EntityActionData) packetData;
				if (eaData.getAction() == PlayerAction.START_SNEAKING) {
					data.getWatcher(getName(action.getName())).setSneaking(!reversed);

					npc.setData(data.getWatcher(getName(action.getName())).getMetadata(new MetadataBuilder(npc.getData())));
				} else if (eaData.getAction() == PlayerAction.STOP_SNEAKING) {
					data.getWatcher(getName(action.getName())).setSneaking(reversed);
					npc.setData(data.getWatcher(getName(action.getName())).getMetadata(new MetadataBuilder(npc.getData())));
				}
				npc.updateMetadata();
			}
			else if (packetData instanceof AnimationData) {
				AnimationData animationData = (AnimationData) packetData;
				npc.animate(animationData.getId());

				if (animationData.getId() == 1 && !VersionUtil.isCompatible(VersionEnum.V1_8)) {
					replayer.getWatchingPlayer().playSound(npc.getLocation(), Sound.ENTITY_PLAYER_HURT, 5F, 5.0F);
				}
			}
			else if (packetData instanceof ChatData) {
				ChatData chatData = (ChatData) packetData;

				replayer.sendMessage(new MessageBuilder(ConfigManager.CHAT_FORMAT)
						.set("name", getName(action.getName()))
						.set("message", chatData.getMessage())
						.build());
			}
			else if (packetData instanceof InvData) {
				InvData invData = (InvData) packetData;

				if (!VersionUtil.isCompatible(VersionEnum.V1_8)) {
					List<WrapperPlayServerEntityEquipment> equipment = VersionUtil.isBelow(VersionEnum.V1_15) ?
							NPCManager.updateEquipment(npc.getId(), invData) : NPCManager.updateEquipmentv16(npc.getId(), invData);
					npc.setLastEquipment(equipment);

					for (WrapperPlayServerEntityEquipment packet : equipment) {
						packet.sendPacket(replayer.getWatchingPlayer());
					}
				} else {
					for (com.comphenix.packetwrapper.old.WrapperPlayServerEntityEquipment packet : NPCManager.updateEquipmentOld(npc.getId(), invData)) {
						packet.sendPacket(replayer.getWatchingPlayer());
					}
				}
			}
			else if (packetData instanceof MetadataUpdate) {
				MetadataUpdate update = (MetadataUpdate) packetData;

				data.getWatcher(getName(action.getName())).setBurning(!reversed && update.isBurning());
				data.getWatcher(getName(action.getName())).setBlocking(!reversed && update.isBlocking());

				WrappedDataWatcher dataWatcher = data.getWatcher(getName(action.getName())).getMetadata(new MetadataBuilder(npc.getData()));
				npc.setData(dataWatcher);

				npc.updateMetadata();
			}
			else if (packetData instanceof ProjectileData) {
				ProjectileData projectile = (ProjectileData) packetData;

				spawnProjectile(projectile, null, replayer.getWatchingPlayer().getWorld(), 0);
			}
			else if (packetData instanceof BlockChangeData) {
				BlockChangeData blockChange = (BlockChangeData) packetData;

				if (reversed) {
					Location location = LocationData.toLocation(blockChange.getLocation()).clone();
					location.setWorld(replayer.getWatchingPlayer().getWorld());
					LocationData loc = LocationData.fromLocation(location);
					blockChange = new BlockChangeData(loc, blockChange.getAfter(), blockChange.getBefore());
				}
				setBlockChange(blockChange);
			}
			else if (packetData instanceof BedEnterData) {
				BedEnterData bed = (BedEnterData) packetData;
				Location location = LocationData.toLocation(bed.getLocation()).clone();
				location.setWorld(replayer.getWatchingPlayer().getWorld());
				LocationData loc = LocationData.fromLocation(location);
				if (VersionUtil.isAbove(VersionEnum.V1_14)) {

					npc.teleport(LocationData.toLocation(loc), true);

					npc.setData(new MetadataBuilder(npc.getData())
							.setPoseField("SLEEPING")
							.getData());

					npc.updateMetadata();
					npc.teleport(LocationData.toLocation(loc), true);

				} else {
					npc.sleep(LocationData.toLocation(loc));
				}
			}
			else if (packetData instanceof EntityItemData) {
				EntityItemData entityData = (EntityItemData) packetData;

				if (entityData.getAction() == 0 && !reversed) {
					spawnItemStack(entityData);
				} else if (entityData.getAction() == 1){
					if (itemEntities.containsKey(entityData.getId())) {
						despawn(Arrays.asList(new Entity[] { itemEntities.get(entityData.getId()) }), null);

						itemEntities.remove(entityData.getId());
					}
				} else {
					if (hooks.containsKey(entityData.getId())) {
						despawn(null, new int[] { hooks.get(entityData.getId()) });

						hooks.remove(entityData.getId());
					}
				}
			}
			else if (packetData instanceof EntityData) {
				EntityData entityData = (EntityData) packetData;
				Location location = LocationData.toLocation(entityData.getLocation()).clone();
				location.setWorld(replayer.getWatchingPlayer().getWorld());
				LocationData loc = LocationData.fromLocation(location);
				if (entityData.getAction() == 0) {
					if (!reversed) {
					IEntity entity = VersionUtil.isCompatible(VersionEnum.V1_8) ? new PacketEntityOld(EntityType.valueOf(entityData.getType()))  : new PacketEntity(EntityType.valueOf(entityData.getType()));
					entity.spawn(LocationData.toLocation(loc), this.replayer.getWatchingPlayer());
					replayer.getEntityList().put(entityData.getId(), entity);
					} else if (replayer.getEntityList().containsKey(entityData.getId())){
						IEntity ent = replayer.getEntityList().get(entityData.getId());
						ent.remove();
					}

				} else if (entityData.getAction() == 1) {
					if (!reversed && replayer.getEntityList().containsKey(entityData.getId())) {
					IEntity ent = replayer.getEntityList().get(entityData.getId());
					ent.remove();
					} else {
						IEntity entity = VersionUtil.isCompatible(VersionEnum.V1_8) ? new PacketEntityOld(EntityType.valueOf(entityData.getType()))  : new PacketEntity(EntityType.valueOf(entityData.getType()));
						entity.spawn(LocationData.toLocation(loc), this.replayer.getWatchingPlayer());
						replayer.getEntityList().put(entityData.getId(), entity);
					}
				}
			}
			else if (packetData instanceof EntityMovingData) {
				EntityMovingData entityMoving = (EntityMovingData) packetData;
;
				if (replayer.getEntityList().containsKey(entityMoving.getId())) {
					IEntity ent = replayer.getEntityList().get(entityMoving.getId());

					if (VersionUtil.isAbove(VersionEnum.V1_15) || VersionUtil.isCompatible(VersionEnum.V1_8)) {
						ent.move(new Location(replayer.getWatchingPlayer().getWorld(), entityMoving.getX(), entityMoving.getY(), entityMoving.getZ()), true, entityMoving.getYaw(), entityMoving.getPitch());
					}

					if (VersionUtil.isBetween(VersionEnum.V1_9, VersionEnum.V1_14)) {
						ent.teleport(new Location(replayer.getWatchingPlayer().getWorld(), entityMoving.getX(), entityMoving.getY(), entityMoving.getZ()), true);
						ent.look(entityMoving.getYaw(), entityMoving.getPitch());
					}
				}
			}
			else if (packetData instanceof EntityAnimationData) {
				EntityAnimationData entityAnimating = (EntityAnimationData) packetData;
				if (replayer.getEntityList().containsKey(entityAnimating.getEntId()) && !reversed) {

					IEntity ent = replayer.getEntityList().get(entityAnimating.getEntId());
					ent.animate(entityAnimating.getId());
				}
			}
			else if (packetData instanceof WorldChangeData) {
				WorldChangeData worldChange = (WorldChangeData) packetData;
				Location loc = LocationData.toLocation(worldChange.getLocation());

				npc.despawn();
				npc.setOrigin(loc);
				npc.setLocation(loc);
				npc.respawn(replayer.getWatchingPlayer());
			}
			else if (packetData instanceof FishingData) {
				FishingData fishing = (FishingData) packetData;
				spawnProjectile(null, fishing, replayer.getWatchingPlayer().getWorld(), npc.getId());
			}
			else if (packetData instanceof VelocityData) {
				VelocityData velocity = (VelocityData) packetData;
				int entID = -1;
				if (hooks.containsKey(velocity.getId())) entID = hooks.get(velocity.getId());
				if (replayer.getEntityList().containsKey(velocity.getId())) entID = replayer.getEntityList().get(velocity.getId()).getId();

				if (entID != -1) {
					WrapperPlayServerEntityVelocity packet = new WrapperPlayServerEntityVelocity();
					packet.setEntityID(entID);
					packet.setVelocityX(velocity.getX());
					packet.setVelocityY(velocity.getY());
					packet.setVelocityZ(velocity.getZ());

					packet.sendPacket(replayer.getWatchingPlayer());
				}
			}
		}

		if (action.getType() == ActionType.DESPAWN || action.getType() == ActionType.DEATH) {
			if (!reversed  && replayer.getNPCList().containsKey(getName(action.getName()))) {
				INPC npc = this.replayer.getNPCList().get(getName(action.getName()));
				npc.remove();
				replayer.getNPCList().remove(getName(action.getName()));

				SpawnData oldSpawnData = new SpawnData(npc.getUuid(), LocationData.fromLocation(npc.getLocation()), signatures.get(getName(action.getName())));
				this.lastSpawnActions.addLast(new ActionData(0, ActionType.SPAWN, getName(action.getName()), oldSpawnData));

				if (action.getType() == ActionType.DESPAWN) {
					replayer.sendMessage(new MessageBuilder(ConfigManager.LEAVE_MESSAGE)
							.set("name", getName(action.getName()))
							.build());
				} else {
					replayer.sendMessage(new MessageBuilder(ConfigManager.DEATH_MESSAGE)
							.set("name", getName(action.getName()))
							.build());
				}

			} else {
				if (!this.lastSpawnActions.isEmpty()) {
					spawnNPC(this.lastSpawnActions.pollLast());
				}
			}

		}
	}

	public void forward() {
		this.replayer.setPaused(true);
		int currentTick = this.replayer.getCurrentTicks();
		int forwardTicks = currentTick + (10 * 20);
		int duration = this.replayer.getReplay().getData().getDuration();

		if ((forwardTicks + 2) >= duration) {
			forwardTicks = duration - 20;
		}

		for (int i = currentTick; i < forwardTicks; i++) {
			this.replayer.executeTick(i, false);
		}
		this.replayer.setCurrentTicks(forwardTicks);
		this.replayer.setPaused(false);
	}

	public void backward() {
		this.replayer.setPaused(true);
		int currentTick = this.replayer.getCurrentTicks();
		int backwardTicks = currentTick - (10 * 20);

		if ((backwardTicks - 2) <= 0) {
			backwardTicks = 1;
		}

		for (int i = currentTick; i > backwardTicks; i--) {
			this.replayer.executeTick(i, true);
		}
		this.replayer.setCurrentTicks(backwardTicks);
		this.replayer.setPaused(false);
	}

	public void jumpTo(Integer seconds) {
		int targetTicks = (seconds * 20);
		int currentTick = replayer.getCurrentTicks();
		if (currentTick > targetTicks) {
			this.replayer.setPaused(true);

			if ((targetTicks - 2) > 0) {
				for (int i = currentTick; i > targetTicks; i--) {
					this.replayer.executeTick(i, true);
				}

				this.replayer.setCurrentTicks(targetTicks);
				this.replayer.setPaused(false);
			}
		} else if (currentTick < targetTicks) {
			this.replayer.setPaused(true);
			int duration = replayer.getReplay().getData().getDuration();

			if ((targetTicks + 2) < duration) {
				for (int i = currentTick; i < targetTicks; i++) {
					this.replayer.executeTick(i, false);
				}
				this.replayer.setCurrentTicks(targetTicks);
				this.replayer.setPaused(false);
			}
		}
	}

	private void spawnNPC(ActionData action) {

		SpawnData spawnData = (SpawnData)action.getPacketData();
		INPC npc = !VersionUtil.isCompatible(VersionEnum.V1_8) ? new PacketNPC(MathUtils.randInt(10000, 20000), spawnData.getUuid(), getName(action.getName())) : new PacketNPCOld(MathUtils.randInt(10000, 20000), spawnData.getUuid(), getName(action.getName()));
		this.replayer.getNPCList().put(getName(action.getName()), npc);
		this.replayer.getReplay().getData().getWatchers().put(getName(action.getName()), new PlayerWatcher(getName(action.getName())));

		int tabMode = Bukkit.getPlayer(getName(action.getName())) != null ? 0 : 2;
		Location spawn = LocationData.toLocation(spawnData.getLocation());

		if(VersionUtil.isCompatible(VersionEnum.V1_8)) {
			npc.setData(new MetadataBuilder(this.replayer.getWatchingPlayer()).resetValue().getData());
		} else {
			npc.setData(new MetadataBuilder(this.replayer.getWatchingPlayer()).setArrows(0).resetValue().getData());

		}

		if (ConfigManager.HIDE_PLAYERS && !getName(action.getName()).equals(this.replayer.getWatchingPlayer().getName())) {
			tabMode = 2;
		}

		if ((spawnData.getSignature() != null && Bukkit.getPlayer(getName(action.getName())) == null) || (spawnData.getSignature() != null && ConfigManager.HIDE_PLAYERS && !getName(action.getName()).equals(this.replayer.getWatchingPlayer().getName()))) {
			WrappedGameProfile profile = new WrappedGameProfile(spawnData.getUuid(), getName(action.getName()));
			WrappedSignedProperty signed = new WrappedSignedProperty(spawnData.getSignature().getName(), spawnData.getSignature().getValue(), spawnData.getSignature().getSignature());
			profile.getProperties().put(spawnData.getSignature().getName(), signed);
			npc.setProfile(profile);

			if (!this.signatures.containsKey(getName(action.getName()))) {
				this.signatures.put(getName(action.getName()), spawnData.getSignature());
			}
		}

		npc.spawn(spawn, tabMode, this.replayer.getWatchingPlayer());
		npc.look(spawnData.getLocation().getYaw(), spawnData.getLocation().getPitch());
	  
	}
	
	private void spawnProjectile(ProjectileData projData, FishingData fishing, World world, int id) {
		if (projData != null && projData.getType() != EntityType.FISHING_HOOK) {
			new BukkitRunnable() {
			
				@Override
				public void run() {
					try {
						Projectile proj = (Projectile) world.spawnEntity(LocationData.toLocation(projData.getSpawn()), projData.getType());
						proj.setVelocity(LocationData.toLocation(projData.getVelocity()).toVector());
					} catch (Exception e) {

					}
				
				}
			}.runTask(ReplaySystem.getInstance());
		} 
		
		if (fishing != null) {			
			int rndID = MathUtils.randInt(2000, 30000);
			AbstractPacket packet = VersionUtil.isCompatible(VersionEnum.V1_8) ? FishingUtils.createHookPacketOld(fishing, id, rndID) : FishingUtils.createHookPacket(fishing, id, rndID);	
			
			hooks.put(fishing.getId(), rndID);
			packet.sendPacket(replayer.getWatchingPlayer());
		}
	}
	
	private void setBlockChange(BlockChangeData blockChange) {
		final Location loc = LocationData.toLocation(blockChange.getLocation());
		
		new BukkitRunnable() {
			
			@SuppressWarnings("deprecation")
			@Override
			public void run() {
				if (blockChange.getAfter().getId() == 0 && blockChange.getBefore().getId() != 0 && MaterialBridge.fromID(blockChange.getBefore().getId()) != Material.FIRE && blockChange.getBefore().getId() != 11 && blockChange.getBefore().getId() != 9) {
					loc.getWorld().playEffect(loc, Effect.STEP_SOUND, blockChange.getBefore().getId(), 15);
					
				}
				int id = blockChange.getAfter().getId();
				int subId = blockChange.getAfter().getSubId();
				
				if (id == 9) id = 8;
				if (id == 11) id = 10;
				
				if (ConfigManager.REAL_CHANGES) {
					if (VersionUtil.isAbove(VersionEnum.V1_13)) {
						loc.getBlock().setType(MaterialBridge.fromID(id), true);
					} else {
						loc.getBlock().setTypeIdAndData(id, (byte) subId, true);
					}
				} else {
					if (VersionUtil.isAbove(VersionEnum.V1_13)) {
						replayer.getWatchingPlayer().sendBlockChange(loc, MaterialBridge.fromID(id), (byte) subId);
					} else {
						replayer.getWatchingPlayer().sendBlockChange(loc, id, (byte) subId);
					}
				}
				
				
			}
		}.runTask(ReplaySystem.getInstance());
	}
	
	private void spawnItemStack(EntityItemData entityData) {
		final Location loc = LocationData.toLocation(entityData.getLocation());
		
		new BukkitRunnable() {
			
			@Override
			public void run() {
				Item item = loc.getWorld().dropItemNaturally(loc, new ItemStack(MaterialBridge.fromID(entityData.getItemData().getId()), 1, (short) entityData.getItemData().getSubId()));
				item.setVelocity(LocationData.toLocation(entityData.getVelocity()).toVector());
				
				itemEntities.put(entityData.getId(), item);
				
			}
		}.runTask(ReplaySystem.getInstance());
	}
	
	public void despawn(List<Entity> entities, int[] ids) {
		
		if (entities != null && entities.size() > 0) {
			new BukkitRunnable() {
			
				@Override
				public void run() {
					for (Entity en : entities) {
						if (en != null) en.remove();
					}
				}
			}.runTask(ReplaySystem.getInstance());
		}
		
		if (ids != null && ids.length > 0) {
			WrapperPlayServerEntityDestroy packet = new WrapperPlayServerEntityDestroy();
			packet.setEntityIds(ids);
			
			packet.sendPacket(replayer.getWatchingPlayer());
		}
	}
    public static HashMap<String, String> names = new HashMap<>();

	public String getName(String originalName) {
		if(names.containsKey(originalName)) {
			return names.get(originalName);
		}
		MongoClient client = new MongoClient();
		MongoDatabase database = client.getDatabase("pac");
		MongoCollection<Document> collection = database.getCollection("inProgress");
		Player watcher = replayer.getWatchingPlayer();
		Document doc = collection.find(new Document("name", watcher.getName())).first();
		if(originalName.equalsIgnoreCase(doc.getString("target"))) {
		  names.put(originalName,"O_Suspeito");
          return "O_Suspeito";
		};
		String nm = randomName();
		int trys = 0;
		while(names.containsValue(nm)) {
			nm = randomName();
			++trys;
			if(trys == 50) {
				nm=originalName.toCharArray().length*100 + "";
			}
		}
		names.put(originalName,nm);
		return nm;
	}

	public String randomName() {
		String[] nameList = {
		  "Canario",
		  "Delta",
		  "Alpha",
		  "Pedro",
		  "Greg",
		  "Jogador",
		  "Porco",
		  "Tigre",
		  "Lobo",
		  "Dente_De_Onca"
		};

		Random random = new Random();
		return nameList[random.nextInt(nameList.length)];
	}
	
	public HashMap<Integer, Entity> getEntities() {
		return itemEntities;
	}
}
