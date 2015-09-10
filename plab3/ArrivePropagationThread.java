/**
 * A Thread that regulary propagates the forwarding table of a server to all connected servers
 * @author dennis
 *
 */
public class ArrivePropagationThread extends Thread {

	
	@Override
	public void run(){
		while(true){
			propagateArrival();
			try {
				//Propagate only every minute
				sleep(60000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void propagateArrival(){
		for(User u:Lobby.dnChat.getUsers().values()){
			if(u.isServer()){
				continue;
			}
			String s="ARRV " + String.format("%.0f", u.getChatId()) + "\r\n" +  u.getChatName() + "\r\n" +  u.getChatDescription()+"\r\n"+u.getHopCount();
			for(User server:Lobby.dnChat.getUsers().values()){
				if(server.isServer()){
					server.getSocket().sendTextAsClient(s);
				}
			}
		}
	}

}
