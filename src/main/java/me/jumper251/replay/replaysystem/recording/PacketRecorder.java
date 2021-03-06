package me.jumper251.replay.replaysystem.recording;

import com.comphenix.packetwrapper.*;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerAction;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType;
import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.filesystem.ConfigManager;
import me.jumper251.replay.listener.AbstractListener;
import me.jumper251.replay.replaysystem.data.types.*;
import me.jumper251.replay.replaysystem.recording.optimization.ReplayOptimizer;
import me.jumper251.replay.utils.VersionUtil;
import me.jumper251.replay.utils.VersionUtil.VersionEnum;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class PacketRecorder extends AbstractListener {

	private PacketAdapter packetAdapter;
	
	private HashMap<String, List<PacketData>> packetData;
	
	private List<Integer> spawnedItems;
	private HashMap<Integer, EntityData> spawnedEntities;
	private HashMap<Integer, String> entityLookup;
	private HashMap<Integer, Entity> idLookup;
	private List<Integer> spawnedHooks;

	private Recorder recorder;
	
	private ReplayOptimizer optimizer;
	
	private AbstractListener compListener, listener;
	
	public PacketRecorder(Recorder recorder) {
		super();
		this.packetData = new HashMap<String, List<PacketData>>();
		this.spawnedItems = new ArrayList<Integer>();
		this.spawnedEntities = new HashMap<Integer, EntityData>();
		this.entityLookup = new HashMap<Integer, String>();
		this.idLookup = new HashMap<Integer, Entity>();
		this.spawnedHooks = new ArrayList<>();
		this.recorder = recorder;
		this.optimizer = new ReplayOptimizer();
	}

	@Override
	public void register() {

		super.register();
		
		this.packetAdapter = new PacketAdapter(ReplaySystem.getInstance(), ListenerPriority.HIGHEST,
				PacketType.Play.Client.POSITION, PacketType.Play.Client.POSITION_LOOK, PacketType.Play.Client.LOOK,
				PacketType.Play.Client.ENTITY_ACTION, PacketType.Play.Client.ARM_ANIMATION, PacketType.Play.Client.BLOCK_DIG,
				PacketType.Play.Server.SPAWN_ENTITY, PacketType.Play.Server.ENTITY_DESTROY, PacketType.Play.Server.ENTITY_VELOCITY,
				PacketType.Play.Server.SPAWN_ENTITY_LIVING, PacketType.Play.Server.REL_ENTITY_MOVE,
				PacketType.Play.Server.REL_ENTITY_MOVE_LOOK, PacketType.Play.Server.ENTITY_TELEPORT) {

            @Override
            public void onPacketReceiving(PacketEvent event) {
				Player p = event.getPlayer();
				if (p != null && recorder.getPlayers().contains(p.getName())) {

					PacketData data = null;
					PacketType packetType = event.getPacketType();
					if (packetType == PacketType.Play.Client.POSITION) {
						WrapperPlayClientPosition packet = new WrapperPlayClientPosition(event.getPacket());
						data = new MovingData(packet.getX(), packet.getY(), packet.getZ(), p.getLocation().getPitch(), p.getLocation().getYaw());
						PlayerWatcher watcher = recorder.getData().getWatcher(p.getName());
						if (watcher.isBurning() && p.getFireTicks() <= 20) {
							watcher.setBurning(false);
							addData(p.getName(), new MetadataUpdate(false, watcher.isBlocking()));
						}
					}
					else if (packetType == PacketType.Play.Client.LOOK) {
						WrapperPlayClientLook packet = new WrapperPlayClientLook(event.getPacket());
						data = new MovingData(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ(), packet.getPitch(), packet.getYaw());
					}
					else if (packetType == PacketType.Play.Client.POSITION_LOOK) {
						WrapperPlayClientPositionLook packet = new WrapperPlayClientPositionLook(event.getPacket());
						data = new MovingData(packet.getX(), packet.getY(), packet.getZ(), packet.getPitch(), packet.getYaw());
					}
					else if (packetType == PacketType.Play.Client.ENTITY_ACTION) {
						WrapperPlayClientEntityAction packet = new WrapperPlayClientEntityAction(event.getPacket());
						if (packet.getAction() == PlayerAction.START_SNEAKING || packet.getAction() == PlayerAction.STOP_SNEAKING) {
							data = new EntityActionData(packet.getAction());
						}
					}
					else if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
						data = new AnimationData(0);
					}
					else if (packetType == PacketType.Play.Client.BLOCK_DIG) {
						WrapperPlayClientBlockDig packet = new WrapperPlayClientBlockDig(event.getPacket());

						if(packet.getStatus() == PlayerDigType.RELEASE_USE_ITEM) {
							PlayerWatcher watcher = recorder.getData().getWatcher(p.getName());
							if (watcher.isBlocking()) {
								watcher.setBlocking(false);
								addData(p.getName(), new MetadataUpdate(watcher.isBurning(), false));
							}
						}
					}
					addData(p.getName(), data);
            	}
            }
            
            @SuppressWarnings("deprecation")
			@Override
            public void onPacketSending(PacketEvent event) {
				Player p = event.getPlayer();
				if (!recorder.getPlayers().contains(p.getName())) return;

				PacketType packetType = event.getPacketType();
				if (packetType == PacketType.Play.Server.SPAWN_ENTITY) {
					WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event.getPacket());

					com.comphenix.packetwrapper.old.WrapperPlayServerSpawnEntity oldPacket = new com.comphenix.packetwrapper.old.WrapperPlayServerSpawnEntity(event.getPacket());
					int type;
					LocationData location;

					if (VersionUtil.isCompatible(VersionEnum.V1_8)) {
						type = oldPacket.getType();
						location = new LocationData(oldPacket.getX(), oldPacket.getY(), oldPacket.getZ(), p.getWorld().getName());
					} else {
						type = packet.getType();
						location = new LocationData(packet.getX(), packet.getY(), packet.getZ(), p.getWorld().getName());
					}

					if ((type == 2 || (VersionUtil.isAbove(VersionEnum.V1_14) && event.getPacket().getEntityTypeModifier().read(0) == EntityType.DROPPED_ITEM)) && !spawnedItems.contains(packet.getEntityID())) {
						Entity en = packet.getEntity(p.getWorld());
						if (en instanceof Item) {
							Item item = (Item) en;
							LocationData velocity = LocationData.fromLocation(item.getVelocity().toLocation(p.getWorld()));

							addData(p.getName(), new EntityItemData(0, packet.getEntityID(), new ItemData(item.getItemStack().getType().getId(), item.getItemStack().getData().getData()), location, velocity));

							spawnedItems.add(packet.getEntityID());
						}
					}
					if ((type == 90 || (VersionUtil.isAbove(VersionEnum.V1_14) && event.getPacket().getEntityTypeModifier().read(0) == EntityType.FISHING_HOOK))  && !spawnedHooks.contains(packet.getEntityID())) {
						location.setYaw(packet.getYaw());
						location.setPitch(packet.getPitch());

						if (VersionUtil.isCompatible(VersionEnum.V1_8)) {
							addData(p.getName(), new FishingData(oldPacket.getEntityID(), location, oldPacket.getOptionalSpeedX(), oldPacket.getOptionalSpeedY(), oldPacket.getOptionalSpeedZ()));
						} else {
							addData(p.getName(), new FishingData(packet.getEntityID(), location, packet.getOptionalSpeedX(), packet.getOptionalSpeedY(), packet.getOptionalSpeedZ()));
						}
						spawnedHooks.add(packet.getEntityID());
					}
				} else if (packetType == PacketType.Play.Server.SPAWN_ENTITY_LIVING && ConfigManager.RECORD_ENTITIES) {
					WrapperPlayServerSpawnEntityLiving packet = new WrapperPlayServerSpawnEntityLiving(event.getPacket());

					EntityType type = packet.getType();
					if (type == null) type = packet.getEntity(p.getWorld()).getType();

					if (!spawnedEntities.containsKey(packet.getEntityID())) {
						LocationData location;

						if (VersionUtil.isCompatible(VersionEnum.V1_8)) {
							com.comphenix.packetwrapper.old.WrapperPlayServerSpawnEntityLiving oldPacket = new com.comphenix.packetwrapper.old.WrapperPlayServerSpawnEntityLiving(event.getPacket());
							location = new LocationData(oldPacket.getX(), oldPacket.getY(), oldPacket.getZ(), p.getWorld().getName());
						} else {
							location = new LocationData(packet.getX(), packet.getY(), packet.getZ(), p.getWorld().getName());
						}

						EntityData entData = new EntityData(0, packet.getEntityID(), location, type.toString());
						addData(p.getName(), entData);

						spawnedEntities.put(packet.getEntityID(), entData);
						entityLookup.put(packet.getEntityID(), p.getName());
						idLookup.put(packet.getEntityID(), packet.getEntity(p.getWorld()));
					}
				} else if (packetType == PacketType.Play.Server.ENTITY_DESTROY) {
					WrapperPlayServerEntityDestroy packet = new WrapperPlayServerEntityDestroy(event.getPacket());

					for (int id : packet.getEntityIDs()) {
						if (spawnedItems.contains(id)) {
							addData(p.getName(), new EntityItemData(1, id, null, null, null));
							spawnedItems.remove(new Integer(id));
						}

						if (spawnedEntities.containsKey(id) && (idLookup.get(id) == null || (idLookup.get(id) != null && idLookup.get(id).isDead()))) {
							addData(p.getName(), new EntityData(1, id, spawnedEntities.get(id).getLocation(), spawnedEntities.get(id).getType()));
							spawnedEntities.remove(id);
							entityLookup.remove(id);
							idLookup.remove(id);
						}

						if (spawnedHooks.contains(id)) {
							addData(p.getName(), new EntityItemData(2, id, null, null, null));
							spawnedHooks.remove(new Integer(id));
						}
					}
				} else if (packetType == PacketType.Play.Server.ENTITY_VELOCITY) {
					WrapperPlayServerEntityVelocity packet = new WrapperPlayServerEntityVelocity(event.getPacket());

					if (spawnedHooks.contains(packet.getEntityID()) || (entityLookup.containsKey(packet.getEntityID()) && entityLookup.get(packet.getEntityID()).equalsIgnoreCase(p.getName()))) {
						addData(p.getName(), new VelocityData(packet.getEntityID(), packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ()));
					}

				} else if (packetType == PacketType.Play.Server.REL_ENTITY_MOVE) {
					WrapperPlayServerRelEntityMove packet = new WrapperPlayServerRelEntityMove(event.getPacket());

					if (entityLookup.containsKey(packet.getEntityID()) && entityLookup.get(packet.getEntityID()).equalsIgnoreCase(p.getName())) {
						addData(p.getName(), new EntityMovingData(packet.getEntityID(), packet.getEntity(p.getWorld()).getLocation()));
					}
				} else if (packetType == PacketType.Play.Server.REL_ENTITY_MOVE_LOOK) {
					WrapperPlayServerRelEntityMoveLook packet = new WrapperPlayServerRelEntityMoveLook(event.getPacket());

					if (entityLookup.containsKey(packet.getEntityID()) && entityLookup.get(packet.getEntityID()).equalsIgnoreCase(p.getName())) {
						addData(p.getName(), new EntityMovingData(packet.getEntityID(), packet.getEntity(p.getWorld()).getLocation()));
					}
				} else if (packetType == PacketType.Play.Server.ENTITY_TELEPORT) {
					WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(event.getPacket());

					if (entityLookup.containsKey(packet.getEntityID()) && entityLookup.get(packet.getEntityID()).equalsIgnoreCase(p.getName())) {
						addData(p.getName(), new EntityMovingData(packet.getEntityID(), packet.getEntity(p.getWorld()).getLocation()));
					}
				}
            }
		};
		
	    ProtocolLibrary.getProtocolManager().addPacketListener(this.packetAdapter);
	    this.registerExternalListeners();
	}
	
	@Override
	public void unregister() {
		super.unregister();
		ProtocolLibrary.getProtocolManager().removePacketListener(this.packetAdapter);
		
		if(this.compListener != null) {
			this.compListener.unregister();
		}
		this.listener.unregister();
	}
	
	private void registerExternalListeners() {
		if (!VersionUtil.isCompatible(VersionEnum.V1_8)) {
			this.compListener = new CompListener(this);
			this.compListener.register();
		}
		
		this.listener = new RecordingListener(this);
		this.listener.register();
	}
	
	public void addData(String name, PacketData data) {
		if (!optimizer.shouldRecord(data)) return;

		List<PacketData> list = packetData.getOrDefault(name, new ArrayList<>());
		
		list.add(data);
		this.packetData.put(name, list);
	}
	
	public HashMap<String, List<PacketData>> getPacketData() {
		return new HashMap<>(packetData);
	}

	public synchronized void removeAll(Set<String> keys) {
		packetData.keySet().removeAll(keys);
	}
	
	public HashMap<Integer, String> getEntityLookup() {
		return entityLookup;
	}
	
	public Recorder getRecorder() {
		return recorder;
	}
	
}
