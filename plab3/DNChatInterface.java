/**
 * The class implementing this Interface should be a Monitor, as each Websocket 
 * has a reference of the SAME DNChat Object. (see DOC/info.png)
 * If the DNChat Object wants to send a Message to a client, it does this by adding the
 * Message(String) in the public outBuffer of the Websocket, e.g. socket.outBuffer.add("Hallo");
 * If the DNChat Object wants to close the connection, it does this by setting the
 * AtomicBoolean doClose of the socket, e.g. socket.doClose = new AtomicBoolean(true);
 * @author dennis
 *
 */
public interface DNChatInterface {

	/**
	 * Adds a Socket as Client to the DNChat
	 * The Client still has to AUTH himself
	 * @param socket - the socket of the client.
	 */
	public void addClient(Websocket socket);
	
	/**
	 * Called by the Sockets of the Client to push a Message (May be Chatmessage or DNChat-Command)
	 * to the DNChatServer. 
	 * @param msg - the message as String 
	 * @param socketID - the ID of the socket on which the msg arrived
	 */
	public void pushMessage(Websocket socket, String msg);
	
	/**
	 * Called to remove Socket/Client from DNChat.
	 * The User then should be logged out and is disconnected.
	 * @param socket
	 * @param socketID
	 */
	public void removeClient(Websocket socket);
	
	
}
