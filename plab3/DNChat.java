import java.rmi.UnexpectedException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The DNChat class which encapsulates the DNChat protocol. 
 *
 */
public class DNChat implements DNChatInterface {

	/**
	 * HashMap handling all open sockets.
	 * Key = socket is, value User
	 */
	private Map<Integer, User> clients;
	
	/**
	 * List of all Users that are connected via a server
	 */
	private List<User> farUsers=Collections.synchronizedList(new LinkedList<User>());
	
	/**
	 * The group pw used to login.
	 */
	private final String pw = "pKQmqLe6";
	//private final String pw = "12345";
	/**
	 * The group description/number.
	 */
	private final String description = "Group 69";
	
	/**
	 *  The maximum amount of characters included in one single message.
	 *  Theoretically limited to Integer.MAX_Value, but we have chosen a
	 *  minor value because of better performance / less waiting time for
	 *  receivers. Therefor a maximum value of 10000 was chosen, although
	 *  a value of the order of 2^24-1 was tested successfully.
	 */

	private final int maxMsgLength = 1000;

	/**
	 * If this is set to true, the server tries to detect server crashes
	 * This might cause problems, e.g. a server with delay may be sensed as down
	 */
	public final boolean SENSE_MODE=true;
	
	
	/**
	 * DNChat constructor called by Lobby.
	 */
	public DNChat(){
		clients = new ConcurrentHashMap<Integer, User>();
		//new ArrivePropagationThread().start();
	}
	
	/**
	 * Adds a client. Called by the corresponding websocket as soon as a TCP connection is established.
	 */
	@Override
	synchronized public void addClient(Websocket socket) {
		User user = new User(socket);
		clients.put(socket.getID(), user);
	}
	/**
	 * Adds a server. Called by WebsocketThread
	 */
	synchronized public void setServer(Websocket socket) {
		User u=clients.get(socket.getID());
		u.setServer(true);
	}
	
	
	/**
	 * Handles incoming messages from other servers
	 * @param socket
	 * @param msg
	 */
	synchronized public void pushMessageServer(Websocket socket, String msg){
		String[] message = msg.split("\r\n");
		String[] head = message[0].split(" ");
		
		switch(head[0]){
		case "SEND":
			User sender=getUser(message[2]);
			Long msgNr = Long.parseLong(head[1]);
			if(sender==null)
				return;
			sender.addMsg(msgNr);
			//Does not have to check conditions in messages, as they are already checked by the server, directly connected to the user
			if(!message[1].equals("*")){
			//Unicast:
				User receiver=getUser(message[1]);
				if(receiver==null){
					//TODO: User with that number does not exsist
					return;
				}
				if(!clients.get(receiver.getSocket().getID()).isServer()){
					msg=message[0]+"\r\n"+sender.getChatId()+"\r\n"+message[3];
				}
				sendToNextHopServer(receiver, msg);
				return;
			}
			//Multicast:
			if(!socket.equals(sender.getSocket())){
				//TODO: Ignore Messages that are not coming from the shortest path to sender when flooding
				return;
			}
			String msgClients=message[0]+"\r\n"+sender.getChatId()+"\r\n"+message[3];
			//Send Message to all sockets, except the one the message was received from
			propagateMsgToClients(msgClients);
			propagateMsgToServers(msg, socket);

			break;
		case "ARRV":
			int hopCount=Integer.valueOf(message[3])+1;
			//Hop count can not be 16
			if (hopCount>15){
				String s="LEFT "+head[1];
				propagateMsgToServers(s, socket);
				if(getUser(head[1])==null || clients.get(getUser(head[1]).getSocket().getID()).getSocket().equals(socket)){
					for(Iterator<User>iterator=farUsers.iterator(); iterator.hasNext();){
						User u = (User) iterator.next();
						if(u.getChatId()==Long.parseLong(head[1])) {
							propagateMsgToClients(s, u);
							iterator.remove();
						}
					}
				}
				return;
			}
			User arriving=getUser(head[1]);
			if(arriving!=null){
				//There is already an other connection to the user over a (possible different) server
				if(arriving.getHopCount()>hopCount){
					//Change Route to User to shorter one
					arriving.setHopCount(hopCount);
					arriving.setSocket(socket);
					//Propagate new HopCount to all Servers, except the one the message was received from
					message[3]=String.valueOf(hopCount);
					msg=message[0]+"\r\n"+message[1]+"\r\n"+message[2]+"\r\n"+message[3];
					propagateMsgToServers(msg, socket);
				}
			}else{
				//There is no connection to the User.
				String descript=message[2];
				arriving=new User(socket, false, hopCount);
				long userId = Long.parseLong(head[1]);
				arriving.setState(User.State.authenticated);
				arriving.setChatDescription(descript);
				arriving.setChatName(message[1]);
				arriving.setId(userId);
				farUsers.add(arriving);
				//Send Message to all sockets, except the one the message was received from
				message[3]=String.valueOf(hopCount);
				msg=message[0]+"\r\n"+message[1]+"\r\n"+message[2]+"\r\n"+message[3];
				propagateMsgToClients(msg);
				propagateMsgToServers(msg, socket);
				unicastMsg(clients.get(getUser(head[1]).getSocket().getID()),"LEFT "+head[1]);
			}
			break;
		case "ACKN":
			User receiver=getUser(message[2]);
			sendToNextHopServer(receiver,msg);
			break;
		case "LEFT":
			User left=getUser(head[1]);
			if(left==null){
				return;
			}
			User otherConnection=Lobby.dnChat.clients.get(left.getSocket().getID());
			//Was I connected to that User over the sending server?
			if(otherConnection.getSocket().equals(socket)){
				farUsers.remove(left);
				propagateMsgToServers(msg, socket);
				propagateMsgToClients(msg, null);
			}else{
				//TODO:THINK HERE COUNT TO INFINITY PROBLEM
				//IDEA OF THIS LINE: "Oh you lost your connection to that user? Well I've got a connection to that user not running over you"
				if(otherConnection!=null)
					unicastMsg(Lobby.dnChat.clients.get(socket.getID()), "ARRV " + left.getChatId() + "\r\n" +  left.getChatName() + "\r\n" +  left.getChatDescription()+"\r\n"+left.getHopCount());
			}
			break;
		}
	}
	/**
	 * Checks if the user is direct connected or over a server and sends the message correctly
	 * @param receiver
	 * @param msg
	 */
	synchronized private void unicastMsg(User receiver, String msg){
		if(receiver.isServer()){
			if(receiver.getSocket().shouldMask()){
				receiver.getSocket().sendTextAsClient(msg);
			}else{
				receiver.getSocket().sendText(msg);
			}
		}else{
			receiver.getSocket().sendText(msg);
		}
	}
	/**
	 * Sends a message to all users that are directly connected to the server
	 */
	synchronized private void propagateMsgToClients(String msg){
		propagateMsgToClients(msg, null);
	}
	
	/**
	 * Sends a message to all users that are directly connected to the server except the User not
	 * @param msg
	 */
	synchronized private void propagateMsgToClients(String msg, User not){
		for(User u: clients.values()){
			if(!u.isServer() && !u.equals(not)){
				u.getSocket().sendText(msg);
			}
		}
	}
	/**
	 * propagates a message to all servers that are directly connected to this server, except socket s
	 * @param msg
	 */
	synchronized private void propagateMsgToServers(String msg, Websocket s){
		for(User u: clients.values()){
			if(!u.getSocket().equals(s) && u.isServer()){
				if(u.getSocket().shouldMask()){
					u.getSocket().sendTextAsClient(msg);
				}else{
					u.getSocket().sendText(msg);
				}
			}
		}
	}
	/**
	 * Sends the message to the next hop (possibly receiver itself) of the receiver
	 */
	synchronized private void sendToNextHopServer(User receiver, String s){
		if(receiver!=null){
			if(clients.containsKey(receiver.getSocket().getID())){
				unicastMsg(clients.get(receiver.getSocket().getID()), s);
			}else{
				try {
					throw new UnexpectedException("This should not happen");
				} catch (UnexpectedException e) {
					// TODO e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Searchs for a the user with the given number
	 * @return
	 */
	synchronized private User getUser(String nr){
		for(User u : clients.values()){
			if(u.getChatId()==Long.parseLong(nr)){
				return u;
			}
		}
		for(Iterator<User>iterator=farUsers.iterator(); iterator.hasNext();){
			User u = (User) iterator.next();
			if(u.getChatId()==Long.parseLong(nr)) {
				return u;
			}
		}
		return null;
	}
	
	/**
	 * Handles the incoming DNChat messages from the users.
	 */
	@Override
	synchronized public void pushMessage(Websocket socket, String msg) {
		//System.out.println("GOT: "+msg);
		String[] message = msg.split("\r\n");
		String[] head = message[0].split(" ");
		String output = "";
		
		User usr = clients.get(socket.getID());
		
		if(usr.isServer()){
			 pushMessageServer(socket, msg);
			 return;
		}
		
		switch (head[0]) {
		
		// Authentication request
		case "AUTH":
			// already in connected state?
			if (usr.getState() != User.State.connected) {
				handleInvdMsg(socket);
				break;
			}
			long userId = Long.parseLong(head[1]);
			// correct login data?
			if (checkLoginData(userId, message[1], message[2], socket)) {
				usr.setId(userId);
				usr.setState(User.State.authenticated);
				usr.setChatDescription(description);
				usr.setChatName(message[1]);
				
				output = "OKAY " +  userId;
				socket.sendText(output);
				
				// ARRV messages sent here
				 String s = "ARRV " + userId + "\r\n" +  usr.getChatName() + "\r\n" +  usr.getChatDescription()+"\r\n"+"0";
				 propagateMsgToClients(s, usr);
				 propagateMsgToServers(s, null);
				 for(User u: clients.values()){
					 String s2 = "ARRV " + u.getChatId() + "\r\n" +  u.getChatName() + "\r\n" +  u.getChatDescription()+"\r\n"+u.getHopCount();
					 if(!u.isServer() && !u.getSocket().equals(usr.getSocket()) && u.getChatId()!=-1){
						 unicastMsg(usr,s2);
					 }
				 }
				 for(User u: farUsers){
					 if(u.getChatId()!=-1){
						 String s2 = "ARRV " +  u.getChatId() + "\r\n" +  u.getChatName() + "\r\n" +  u.getChatDescription()+"\r\n"+u.getHopCount();
						 unicastMsg(usr,s2); 
					 }
				 }
			}
			break;

		// SEND request
		case "SEND":
			if (usr.getState() != User.State.authenticated) {
				handleInvdMsg(socket);
				break;
			}
			Long msgNr = Long.parseLong(head[1]);
			// TODO text size
			if (message[2].length() > maxMsgLength) {
				output = "FAIL " +  msgNr + "\r\n" + "LENGTH";
				socket.sendText(output);
				break;
			}
			// check whether msg id redundant or not.
			if (!checkMsg(msgNr)) {
				output = "FAIL " + msgNr + "\r\n" + "NUMBER";
				socket.sendText(output);
				break;
			} else {
				// everything ok. add msg and notify sender...
				usr.addMsg(msgNr);
				output = "OKAY " + msgNr;
				socket.sendText(output);
			}
			// ... and send message to receiver(s)
			String s = "SEND " +  msgNr + "\r\n"+ message[1]+ "\r\n" + usr.getChatId() + "\r\n" + message[2];
			String sClient="SEND "+msgNr+"\r\n"+usr.getChatId()+"\r\n"+message[2];
			// to all currently logged in users
			if (message[1].equals("*")) {
				propagateMsgToClients(sClient, usr);
				propagateMsgToServers(s, null);
			} else {
				User receiver=getUser(message[1]);
				//Send the message to the next hop of receiver (possibly receiver himself)
				if(receiver!=null){
					if(!clients.get(receiver.getSocket().getID()).isServer()){
						s=sClient;
					}
					sendToNextHopServer(receiver,s);
				}else{
					// No receiver with given userId found. Send FAIL msg to sender.
					output = "FAIL " + msgNr + "\r\n" + "NUMBER";
					socket.sendText(output);	
				}
				break;
			}
			break;
		
		// Acknowledgement 
		case "ACKN":
			// Correct state? Otherwise disconnect user.
			if (usr.getState() != User.State.authenticated) {
				handleInvdMsg(socket);
				break;
			}
			//Check if msgId was sent before/exists.
			Long msgId = Long.parseLong(head[1]);
			User sender=getSender(msgId);
			if(sender==null){
				// msg does not exit. send FAIL message back to sender.
				output = "FAIL " + msgId + "\r\n" + "NUMBER";
				socket.sendText(output);
				break;
			} else {
				// message exists.
				output = "ACKN " + head[1] + "\r\n" + usr.getChatId()+"\r\n"+ String.valueOf(sender.getChatId());
			}
			// inform other user.
			sendToNextHopServer(sender,output);
			break;
		
		case "SRVR":
			if(usr.hasSendMessages()){
				handleInvdMsg(socket);
				break;
			}
			if(!head[1].equals(String.valueOf(0))){
				usr.getSocket().setMask(false);
				usr.setServer(true);
				usr.setHopCount(1);
				propagateArrivals(usr);
				break;
			}else{
				handleInvdMsg(socket);
				break;
			}
		// Invalid messages handled here. Disconnect user.
		default:
			handleInvdMsg(socket);
			break;
		}
	}
	/**
	 * Propagates all connected users to the user usr
	 * @param usr
	 */
	synchronized public void propagateArrivals(User usr){
		for(Iterator<User> iter=clients.values().iterator(); iter.hasNext();){
			User u=iter.next();
			if(!u.isServer() && u.getChatId()!=-1){
				String ar="ARRV " + u.getChatId() + "\r\n" +  u.getChatName() + "\r\n" +  u.getChatDescription()+"\r\n"+u.getHopCount();
				unicastMsg(usr, ar);
			}
		}
		for(Iterator<User> iter=farUsers.iterator(); iter.hasNext();){
			User u=iter.next();
			if(!u.isServer() && u.getChatId()!=-1){
				String ar="ARRV " + u.getChatId() + "\r\n" +  u.getChatName() + "\r\n" +  u.getChatDescription()+"\r\n"+u.getHopCount();
				unicastMsg(usr, ar);
			}
		}
	}
	
/**
 * Returns the User object representing the sender of the message with id msgId
 * @param msgId
 * @return
 */
	synchronized private User getSender(Long msgId) {
		for (Iterator<User> iterator = clients.values().iterator(); iterator.hasNext();) {
			User u = (User) iterator.next();
			if(u.getMessagesSent().contains(msgId)) {
				return u;
			}
		}
		for(Iterator<User>iterator=farUsers.iterator(); iterator.hasNext();){
			User u = (User) iterator.next();
			if(u.getMessagesSent().contains(msgId)) {
				return u;
			}
		}
		return null;
	}

	/**
	 * Checks a msgNr if it has been used already before.
	 * @param msgNr
	 * @return true if not used before, false otherwise.
	 */
	synchronized private boolean checkMsg(Long msgNr) {
		for (Iterator<User> iterator = clients.values().iterator(); iterator.hasNext();) {
			User u = (User) iterator.next();
			if (u.getMessagesSent().contains(msgNr)) {
				return false;
			}
		}
		for(Iterator<User> iterator = farUsers.iterator(); iterator.hasNext();){
			User u = (User) iterator.next();
			if(u.getMessagesSent().contains(msgNr)){
				return false;
			}
		}
		return true;
	}

	/**
	 * Removes user from client collection and
	 * notifies all users that some user with
	 * the given socket (id) has left the chat.
	 */
	@Override
	synchronized public void removeClient(Websocket socket) {
		User leftUser = clients.remove(socket.getID());
		if(!leftUser.isServer()){
			String s = "LEFT " + leftUser.getChatId();
			for (Iterator<User> iterator = clients.values().iterator(); iterator.hasNext();) {
				User u = (User) iterator.next();
				unicastMsg(u, s);
			}
		}
		socket.doClose.set(true);
	}

	/**
	 * Handles a invalid message and disconnects the responsible client.
	 * @param websocket
	 */
	synchronized private void handleInvdMsg(Websocket websocket) {
		String output = "INVD 0";
		unicastMsg(clients.get(websocket.getID()), output);
		removeClient(websocket);
	}
	
	/**
	 * Checks user login data:
	 * pw correct? name and id not in use yet?
	 * Socket is used to send reply in case of failure.
	 * @param id, name, pw, socket
	 * @return true if user id, pw and name is okay, false otherwise.
	 */
	synchronized private boolean checkLoginData(long id, String name, String pw, Websocket socket) {		
		String failMsg = "FAIL " +  id + "\r\n";
		if (!pw.equals(this.pw)) {
			failMsg += "PASSWORD";
			socket.sendText(failMsg);
			return false;
		}
		for (Iterator<User> iterator = clients.values().iterator(); iterator.hasNext();) {
			User u = (User) iterator.next();
			if(u.isServer()){
				continue;
			}
			if (u.getChatId() == id) {
				failMsg += "NUMBER";
				socket.sendText(failMsg);
				return false;
			}
			if (name.equals("") || u.getChatName().equals(name)) {
				failMsg += "NAME";
				socket.sendText(failMsg);
				return false;
			}
			
		}
		for (Iterator<User> iterator = farUsers.iterator(); iterator.hasNext();) {
			User u = (User) iterator.next();
			
			if (u.getChatId() == id) {
				failMsg += "NUMBER";
				socket.sendText(failMsg);
				return false;
			}
			if (name.equals("") || u.getChatName().equals(name)) {
				failMsg += "NAME";
				socket.sendText(failMsg);
				return false;
			}
			
		}
		return true;
	}
	
	synchronized public List<User> getFarUsers(){
		return farUsers;
	}
	
	synchronized public Map<Integer,User> getUsers(){
		return clients;
	}
	
	/**
	 * Called when a server is down, removes the clients from that server
	 * @param server
	 */
	@SuppressWarnings("unused")
	synchronized public void reportServerDown(Websocket server){
		if(SENSE_MODE==false){
			return;
		}
		if(clients.get(server.getID())==null || !clients.get(server.getID()).isServer()){
			return;
		}
		clients.remove(server.getID());
		for(Iterator<User> iter=farUsers.iterator(); iter.hasNext();){
			User u=iter.next();
			if(u.getSocket().equals(server)){
				String msg="LEFT " + u.getChatId();
				propagateMsgToServers(msg, server);
				propagateMsgToClients(msg, null);
				iter.remove();
			}
		}
		server.doClose.set(true);
		System.err.println("The connection to another server is down!");
		
	}
	
	synchronized public String getPassword(){
		return pw;
	}
}
