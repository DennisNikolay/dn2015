import java.util.LinkedList;

/**
 * The user class representing the clients connected to the server.
 * @author Mo
 *
 */
public class User {
	
	public enum State{
		disconnected,
		connected,
		authenticated
	}
	
	/**
	 * The user's websocket transporting in and outcoming messages.	
	 */
	private Websocket socket;
	
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
	
	private boolean isServer;
	private int hopCount;
		
	public User(Websocket socket) {
		this.socket = socket;
		state = State.connected;
		messagesSent = new LinkedList<Double>();
		isServer=false;
		hopCount=0;
	}
	public User(Websocket socket, boolean server, int hops) {
		this.socket = socket;
		state = State.connected;
		messagesSent = new LinkedList<Double>();
		setServer(server);
		hopCount=hops;
	}
	
	synchronized public State getState() {
		return state;
	}
		
	synchronized public void setState(State state) {
		this.state = state;
	}
		
	synchronized public void setId(double id) {
		this.chatId = id;
	}

	synchronized public void setChatName(String chatName) {
		this.chatName = chatName;
	}

	synchronized public void setChatDescription(String chatDescription) {
		this.chatDescription = chatDescription;
	}

	synchronized public Websocket getSocket() {
		return socket;
	}
	
	synchronized public void setSocket(Websocket s){
		socket=s;
	}

	synchronized public String getChatName() {
		return chatName;
	}
	
	synchronized public double getChatId() {
		return chatId;
	}

	synchronized public String getChatDescription() {
		return chatDescription;
	}

	synchronized public LinkedList<Double> getMessagesSent() {
		return messagesSent;
	}

	synchronized public void addMsg(Double m) {
		messagesSent.add(m);
	}

	@Override
	synchronized public String toString() {
		return "User [socket=" + socket + ", chatId=" + chatId + ", chatName="
				+ chatName + ", state=" + state + "]";
	}
	
	public void setServer(boolean b){
		isServer=b;
		//if(isServer){
		//	ArrivePropagationThread.propagateArrival();
		//}
	}
	
	synchronized public boolean isServer(){
		return isServer;
	}
	
	synchronized public boolean hasSendMessages(){
		return !messagesSent.isEmpty();
	}

	synchronized public int getHopCount() {
		return hopCount;
	}
	synchronized public void setHopCount(int hopCount) {
		this.hopCount = hopCount;
	}
	
}
