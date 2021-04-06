package me.jumper251.replay.replaysystem.data.types;

import org.bukkit.Location;

public class EntityMovingData extends PacketData {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3792160902735306458L;

	private float x, y, z;
	
	private int id;
	
	private float pitch, yaw;
	
	public EntityMovingData(int id, Location loc) {
		this.x = (float) loc.getX();
		this.y = (float) loc.getY();
		this.z = (float) loc.getZ();
		this.id = id;
		this.pitch = loc.getPitch();
		this.yaw = loc.getYaw();
	}
	
	public double getY() {
		return y;
	}
	
	public float getPitch() {
		return pitch;
	}
	
	public double getX() {
		return x;
	}
	
	public float getYaw() {
		return yaw;
	}
	
	public double getZ() {
		return z;
	}
	
	public int getId() {
		return id;
	}
	
}
