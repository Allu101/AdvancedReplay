package me.jumper251.replay.replaysystem.utils;

import com.comphenix.packetwrapper.old.WrapperPlayServerEntityEquipment;
import com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot;
import com.comphenix.protocol.wrappers.Pair;
import me.jumper251.replay.replaysystem.data.types.InvData;
import me.jumper251.replay.replaysystem.data.types.ItemData;
import me.jumper251.replay.utils.MaterialBridge;
import me.jumper251.replay.utils.VersionUtil;
import me.jumper251.replay.utils.VersionUtil.VersionEnum;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NPCManager {

	public static List<String> names = new ArrayList<String>();
	
	private final static List<Material> ARMOR = Arrays.asList(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
			Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
			Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
			Material.GOLD_HELMET, Material.GOLD_CHESTPLATE, Material.GOLD_LEGGINGS, Material.GOLD_BOOTS,
			Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);

	public static List<com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment> updateEquipmentv16(int id, InvData data) {
		List<Pair<ItemSlot, ItemStack>> items = new ArrayList<>();
		items.add(new Pair<>(ItemSlot.HEAD, fromID(data.getHead())));
		items.add(new Pair<>(ItemSlot.CHEST, fromID(data.getChest())));
		items.add(new Pair<>(ItemSlot.LEGS, fromID(data.getLeg())));
		items.add(new Pair<>(ItemSlot.FEET, fromID(data.getBoots())));
		items.add(new Pair<>(ItemSlot.MAINHAND, fromID(data.getMainHand())));
		items.add(new Pair<>(ItemSlot.OFFHAND, fromID(data.getOffHand())));

		com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment packet = new com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment();

		packet.setEntityID(id);
		packet.getHandle().getSlotStackPairLists().write(0, items);

		return Collections.singletonList(packet);
	}

	public static List<com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment> updateEquipment(int id, InvData data) {
		List<com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment> list = new ArrayList<com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment>();

		com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment packet = createEquipment(id, ItemSlot.HEAD);
		packet.setItem(fromID(data.getHead()));
		list.add(packet);

		com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment packet1 = createEquipment(id, ItemSlot.CHEST);
		packet1.setItem(fromID(data.getChest()));
		list.add(packet1);

		com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment packet2 = createEquipment(id, ItemSlot.LEGS);
		packet2.setItem(fromID(data.getLeg()));
		list.add(packet2);

		com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment packet3 = createEquipment(id, ItemSlot.FEET);
		packet3.setItem(fromID(data.getBoots()));
		list.add(packet3);

		com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment packet4 = createEquipment(id, ItemSlot.MAINHAND);
		packet4.setItem(fromID(data.getMainHand()));
		list.add(packet4);
		
		if(!VersionUtil.isCompatible(VersionEnum.V1_8)) {
			com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment packet5 = createEquipment(id, ItemSlot.OFFHAND);
			packet5.setItem(fromID(data.getOffHand()));
			list.add(packet5);
		}
		return list;
	}
	
	public static List<WrapperPlayServerEntityEquipment> updateEquipmentOld(int id, InvData data) {
		List<WrapperPlayServerEntityEquipment> list = new ArrayList<>();

		WrapperPlayServerEntityEquipment packet = createEquipmentOld(id, 4);
		packet.setItem(fromID(data.getHead()));
		list.add(packet);

		WrapperPlayServerEntityEquipment packet1 = createEquipmentOld(id, 3);
		packet1.setItem(fromID(data.getChest()));
		list.add(packet1);

		WrapperPlayServerEntityEquipment packet2 = createEquipmentOld(id, 2);
		packet2.setItem(fromID(data.getLeg()));
		list.add(packet2);

		WrapperPlayServerEntityEquipment packet3 = createEquipmentOld(id, 1);
		packet3.setItem(fromID(data.getBoots()));
		list.add(packet3);

		WrapperPlayServerEntityEquipment packet4 = createEquipmentOld(id, 0);
		packet4.setItem(fromID(data.getMainHand()));
		list.add(packet4);

		return list;
	}
	
	public static ItemStack fromID(ItemData data) {
		if (data == null) return new ItemStack(Material.AIR);
		return new ItemStack(MaterialBridge.fromID(data.getId()), 1, (short)data.getSubId());
	}
	
	@SuppressWarnings("deprecation")
	public static ItemData fromItemStack(ItemStack stack) {
		if (stack == null) return null;
		return new ItemData(stack.getType().getId(), stack.getData().getData());
	}
	
	@SuppressWarnings("deprecation")
	public static InvData copyFromPlayer(Player player, boolean armor, boolean off) {
		InvData data = new InvData();

		PlayerInventory pInv = player.getInventory();
		if (VersionUtil.isCompatible(VersionEnum.V1_8)) {
			data.setMainHand(fromItemStack(player.getItemInHand()));
		} else {
			data.setMainHand(fromItemStack(pInv.getItemInMainHand()));
			if (off) {
				data.setOffHand(fromItemStack(pInv.getItemInOffHand()));
			}
		}
		
		if (armor) {
			data.setHead(fromItemStack(pInv.getHelmet()));
			
			data.setChest(fromItemStack(pInv.getChestplate()));
			
			data.setLeg(fromItemStack(pInv.getLeggings()));
			
			data.setBoots(fromItemStack(pInv.getBoots()));
		}
		return data;
	}
	
	public static boolean isArmor(ItemStack stack) {
		if (stack == null) return false;
		return ARMOR.contains(stack.getType());
	}
	
	public static String getArmorType(ItemStack stack) {
		if (stack == null) return null;
		
		if (stack.getType().toString().contains("HELMET")) return "head";
		if (stack.getType().toString().contains("CHESTPLATE")) return "chest";
		if (stack.getType().toString().contains("LEGGINGS")) return "leg";
		if (stack.getType().toString().contains("BOOTS")) return "boots";
		
		return null;
	}
	
	public static boolean wearsArmor(Player p, String type) {
		PlayerInventory inv = p.getInventory();
		if (type == null) return false;
		
		if (type.equals("head") && isArmor(inv.getHelmet())) return true;
		if (type.equals("chest") && isArmor(inv.getChestplate())) return true;
		if (type.equals("leg") && isArmor(inv.getLeggings())) return true;
		if (type.equals("boots") && isArmor(inv.getBoots())) return true;

		return false;
	}
	 
	private static com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment createEquipment(int id, ItemSlot slot) {
		com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment packet = new com.comphenix.packetwrapper.WrapperPlayServerEntityEquipment();
		packet.setEntityID(id);
		packet.setSlot(slot);
		return packet;
	}

	private static WrapperPlayServerEntityEquipment createEquipmentOld(int id, int slot) {
		WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment();
		packet.setEntityID(id);
		packet.setSlot(slot);
		return packet;
	}
	
}
