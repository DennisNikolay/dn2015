import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * Class that checks StdIn for connect or exit commands
 * @author dennis
 *
 */
public class ConnectThread extends Thread {

	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run(){
		//FOR PL3
		Scanner d=new Scanner(System.in);
		d.useDelimiter("\n");
		while(!this.isInterrupted()){
			if(d.hasNext()){
				boolean b=true;
				String msg=d.next();
				String[] con=msg.split(" ");
				if(!con[0].equals("connect") || con.length<2){
					b=false;
				}
				if(con[0].equals("exit")){
					for(User u: Lobby.dnChat.getUsers().values()){
						u.getSocket().doClose.set(true);
					}
					Lobby.shouldTerminate.set(true);
					try {
						Lobby.httpSocket.close();
					} catch (IOException e) {}
					Thread.currentThread().interrupt();
				}
				if(b){
					try {
						int port=42015;
						if(con.length==3){
							port=Integer.valueOf(con[2]);
						}
						Socket s=new Socket(con[1], port );
						new WebsocketThread(s).start();
					} catch (NumberFormatException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}
		}

	}
}
