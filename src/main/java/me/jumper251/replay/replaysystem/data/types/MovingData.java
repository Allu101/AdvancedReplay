package me.jumper251.replay.replaysystem.data.types;


public class MovingData extends PacketData {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3792160902735306458L;

	private float x, y, z;
	
	private float pitch, yaw;
	
	public MovingData(double x, double y, double z, float pitch, float yaw) {
		this.x = (float) x;
		this.y = (float) y;
		this.z = (float) z;
		this.pitch = pitch;
		this.yaw = yaw;
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
	
}
