import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class DNChatPl3 implements DNChatInterface {
	/**
	 * HashMap handling all open sockets.
	 * Key = socket is, value User
	 */
	private Map<Integer, UserPl3> clients;
		
	/**
	 * The group pw used to login.
	 */
	private final String pw = "pKQmqLe6";
	
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
	private final int maxMsgLength = 10000000;

	/**
	 * DNChat constructor called by Lobby.
	 */
	public DNChatPl3(){
		clients = new HashMap<Integer, UserPl3>();
	}
	
	/**
	 * Adds a client. Called by the corresponding websocket as soon as a TCP connection is established.
	 */
	@Override
	public void addClient(Websocket socket) {
		UserPl3 user = new UserPl3(socket,0);
		clients.put(socket.getID(), user);
	}

	/**
	 * Handles the incoming DNChat messages from the users.
	 */
	@Override
	public void pushMessage(Websocket socket, String msg) {

		String[] message = msg.split("\r\n");
		String[] head = message[0].split(" ");
		String output = "";
		
		UserPl3 usr = clients.get(socket.getID());
		switchCase: switch (head[0]) {
		
		// Authentication request
		case "AUTH":
			// already in connected state?
			if (usr.getState() != UserPl3.State.connected) {
				handleInvdMsg(socket);
				break;
			}
			double userId = Double.parseDouble(head[1]);
			// correct login data?
			if (checkLoginData(userId, message[1], message[2], socket)) {
				usr.setId(userId);
				usr.setState(UserPl3.State.authenticated);
				usr.setChatDescription(description);
				usr.setChatName(message[1]);
				
				output = "OKAY " + String.format("%.0f", userId);
				socket.sendText(output);
				
				// ARRV messages sent here
				for (Iterator<UserPl3> iterator = clients.values().iterator(); iterator
						.hasNext();) {
					UserPl3 u = (UserPl3) iterator.next();
					if (!socket.equals(u.getSocket())) {
						 // Tells the other users that someone new has joined the chat.
						 String s = "ARRV " + String.format("%.0f", userId) + "\r\n" +  usr.getChatName() + "\r\n" +  usr.getChatDescription();
						 u.getSocket().sendText(s);
						 
						 // Tells the new logged in user, who is already chatting.
						 if (u.getState() == UserPl3.State.authenticated) {
							 s = "ARRV " + String.format("%.0f", u.getChatId()) + "\r\n" +  u.getChatName() + "\r\n" +  u.getChatDescription();
							 socket.sendText(s);
						}
						 
					}	
				}
			}
			break;

		// SEND request
		case "SEND":
			if (usr.getState() != UserPl3.State.authenticated) {
				handleInvdMsg(socket);
				break;
			}
			Double msgNr = Double.parseDouble(head[1]);
			// TODO text size
			if (message[2].length() > maxMsgLength) {
				output = "FAIL " + String.format("%.0f", msgNr) + "\r\n" + "LENGTH";
				socket.sendText(output);
				break;
			}
			// check whether msg id redundant or not.
			if (!checkMsg(msgNr)) {
				output = "FAIL " + String.format("%.0f", msgNr) + "\r\n" + "NUMBER";
				socket.sendText(output);
				break;
			} else {
				// everything ok. add msg and notify sender...
				usr.addMsg(msgNr);
				output = "OKAY " + String.format("%.0f", msgNr);
				socket.sendText(output);
			}
			// ... and send message to receiver(s)
			String s = "SEND " + String.format("%.0f", msgNr) + "\r\n" + String.format("%.0f", usr.getChatId()) + "\r\n" + message[2];
			// to all currently logged in users
			if (message[1].equals("*")) {
				for (Iterator<UserPl3> iterator = clients.values().iterator(); iterator
						.hasNext();) {
					UserPl3 u = (UserPl3) iterator.next();
					if (!socket.equals(u.getSocket())) {
						u.getSocket().sendText(s);
					}
				} // or to a specific user
			} else {
				double receiverId = Double.parseDouble(message[1]);
				for (Iterator<UserPl3> iterator = clients.values().iterator(); iterator
						.hasNext();) {
					UserPl3 u = (UserPl3) iterator.next();
					// Receiver found. Leave switch case.
					if (u.getChatId() == receiverId) {
						u.getSocket().sendText(s);
						break switchCase;
					}
				}
				// No receiver with given userId found. Send FAIL msg to sender.
				output = "FAIL " + String.format("%.0f", msgNr) + "\r\n" + "NUMBER";
				socket.sendText(output);
				break;
			}
			break;
		
		// Acknowledgement 
		case "ACKN":
			// Correct state? Otherwise disconnect user.
			if (usr.getState() != UserPl3.State.authenticated) {
				handleInvdMsg(socket);
				break;
			}
			//Check if msgId was sent before/exists.
			Double msgId = Double.parseDouble(head[1]);
			if(!msgExists(msgId)){
				// msg does not exit. send FAIL message back to sender.
				output = "FAIL " + String.format("%.0f", msgId) + "\r\n" + "NUMBER";
				socket.sendText(output);
				break;
			} else {
				// message exists.
				output = "ACKN " + head[1] + "\r\n" + String.format("%.0f", usr.getChatId());
			}
			// inform other users.
			for (Iterator<UserPl3> iterator = clients.values().iterator(); iterator.hasNext();) {
				UserPl3 u = (UserPl3) iterator.next();
				if (!socket.equals(u.getSocket())) {
					u.getSocket().sendText(output);
				}
			}
			break;
		case "SRVR":
			if(head[1]!=String.valueOf(0)){
				handleInvdMsg(socket);
			}
			break;
		case "ARRV":
			
		// Invalid messages handled here. Disconnect user.
		default:
			handleInvdMsg(socket);
			break;
		}
	}
	

	private boolean msgExists(Double msgId) {
		for (Iterator<UserPl3> iterator = clients.values().iterator(); iterator.hasNext();) {
			UserPl3 u = (UserPl3) iterator.next();
			if(u.getMessagesSent().contains(msgId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks a msgNr if it has been used already before.
	 * @param msgNr
	 * @return true if not used before, false otherwise.
	 */
	private boolean checkMsg(Double msgNr) {
		for (Iterator<UserPl3> iterator = clients.values().iterator(); iterator.hasNext();) {
			UserPl3 u = (UserPl3) iterator.next();
			if (u.getMessagesSent().contains(msgNr)) {
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
	public void removeClient(Websocket socket) {
		UserPl3 leftUser = clients.remove(socket.getID());
		socket.doClose.set(true);
		String s = "LEFT " + String.format("%.0f", leftUser.getChatId());
		for (Iterator<UserPl3> iterator = clients.values().iterator(); iterator.hasNext();) {
			UserPl3 u = (UserPl3) iterator.next();
			u.getSocket().sendText(s);
		}
	}

	/**
	 * Handles a invalid message and disconnects the responsible client.
	 * @param websocket
	 */
	private void handleInvdMsg(Websocket websocket) {
		String output = "INVD 0";
		websocket.sendText(output);
		removeClient(websocket);
	}
	
	/**
	 * Checks user login data:
	 * pw correct? name and id not in use yet?
	 * Socket is used to send reply in case of failure.
	 * @param id, name, pw, socket
	 * @return true if user id, pw and name is okay, false otherwise.
	 */
	private boolean checkLoginData(double id, String name, String pw, Websocket socket) {		
		String failMsg = "FAIL " + String.format("%.0f", id) + "\r\n";
		if (!pw.equals(this.pw)) {
			failMsg += "PASSWORD";
			socket.sendText(failMsg);
			return false;
		}
		for (Iterator<UserPl3> iterator = clients.values().iterator(); iterator.hasNext();) {
			UserPl3 u = (UserPl3) iterator.next();
			
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


}
