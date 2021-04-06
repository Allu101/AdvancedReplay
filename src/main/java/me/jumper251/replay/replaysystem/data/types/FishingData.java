package me.jumper251.replay.replaysystem.data.types;

public class FishingData extends PacketData {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3909142114596921006L;
	
	
	private LocationData location;
	
	private float x, y, z;
	
	private int id;
	
	public FishingData(int id, LocationData location, double x, double y, double z)  {
		this.location = location;
		this.x = (float) x;
		this.y = (float) y;
		this.z = (float) z;
		this.id = id;
	}
	
	public LocationData getLocation() {
		return location;
	}
	
	public double getX() {
		return x;
	}
	
	public double getY() {
		return y;
	}
	
	public double getZ() {
		return z;
	}
	
	public int getId() {
		return id;
	}

}
