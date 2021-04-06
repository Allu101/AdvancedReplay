package me.jumper251.replay.replaysystem.data.types;

public class ChatData extends PacketData {

    /**
     *
     */
    private static final long serialVersionUID = 6849586468365004854L;

    private byte[] message;

    public ChatData(String message) {
        this.message = message.getBytes();
    }

    public String getMessage() {
        return message.toString();
    }
}
