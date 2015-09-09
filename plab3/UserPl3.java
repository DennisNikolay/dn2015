import java.util.LinkedList;



public class UserPl3 {

	
	public enum State{
		disconnected,
		connected,
		authenticated
	}
	
	/**
	 * The user's websocket transporting in and outcoming messages.	
	 */
	private final Websocket socket;
	
	/**
	 * The user's unique chat id.
	 */
	private double chatId = -1;
	
	/**
	 * The user's chat name.
	 */
	private String chatName = "";
	
	/**
	 * The user's description displayed to others.
	 */
	private String chatDescription = "";
	
	/**
	 * The user's state according to DNChat - disconnected, connected, authenticated.
	 */
	private State state;
	
	/**
	 * A list containing all messages which had been sent by the user.
	 */
	private LinkedList<Double> messagesSent;
	
	private int hops=0;
	
	public UserPl3(Websocket socket, int hops) {
		this.socket = socket;
		this.hops=hops;
		state = State.connected;
		messagesSent = new LinkedList<Double>();
	}
		
	public State getState() {
		return state;
	}
		
	public void setState(State state) {
		this.state = state;
	}
		
	public void setId(double id) {
		this.chatId = id;
	}

	public void setChatName(String chatName) {
		this.chatName = chatName;
	}

	public void setChatDescription(String chatDescription) {
		this.chatDescription = chatDescription;
	}

	public Websocket getSocket() {
		return socket;
	}

	public String getChatName() {
		return chatName;
	}
	
	public double getChatId() {
		return chatId;
	}

	public String getChatDescription() {
		return chatDescription;
	}

	public LinkedList<Double> getMessagesSent() {
		return messagesSent;
	}

	public void addMsg(Double m) {
		messagesSent.add(m);
	}

	@Override
	public String toString() {
		return "User [socket=" + socket + ", chatId=" + chatId + ", chatName="
				+ chatName + ", state=" + state + "]";
	}

}
