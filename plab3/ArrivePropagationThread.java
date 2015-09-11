/**
 * A Thread that regulary propagates the forwarding table of a server to all connected servers
 * @author dennis
 *
 */
public class ArrivePropagationThread extends Thread {

	
	@Override
	public void run(){
		while(!this.isInterrupted()){
			propagateArrival();
			try {
				//Propagate only every minute
				sleep(60000);
			} catch (InterruptedException e) {
				this.interrupt();
			}
		}
	}
	
	public static void propagateArrival(){
		for(User u:Lobby.dnChat.getUsers().values()){
			if(!u.isServer() && u.getChatId()!=-1){
				String s="ARRV " + u.getChatId() + "\r\n" +  u.getChatName() + "\r\n" +  u.getChatDescription()+"\r\n"+u.getHopCount();
				for(User server:Lobby.dnChat.getUsers().values()){
					if(server.isServer()){
						if(server.getSocket().shouldMask()){
							server.getSocket().sendTextAsClient(s);
						}else{
							server.getSocket().sendText(s);
						}
					}
				}
			}
		}
	}

}
