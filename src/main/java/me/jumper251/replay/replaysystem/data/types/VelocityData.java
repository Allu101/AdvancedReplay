package me.jumper251.replay.replaysystem.data.types;

public class VelocityData extends PacketData {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4046621273672598227L;
	
	private float x, y, z;
	
	private int id;
	
	public VelocityData(int id, double x, double y, double z) {
		this.id = id;
		this.y = (float) y;
		this.x = (float) x;
		this.z = (float) z;
	}
	
	public int getId() {
		return id;
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

}
