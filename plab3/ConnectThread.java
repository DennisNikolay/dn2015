import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * Class that checks StdIn for connect or exit commands
 *
 */
public class ConnectThread extends Thread {

	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run(){
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
					synchronized(DNChat.class){
						for(User u: Lobby.dnChat.getUsers().values()){
							u.getSocket().doClose.set(true);
						}
					}
					Lobby.shouldTerminate.set(true);
					try {
						Lobby.httpSocket.close();
					} catch (IOException e) {
						//TODO: e.printStackTrace();
					}
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
						// TODO e.printStackTrace();
						System.out.println("The third argument must be a valid portnumber");
					} catch (UnknownHostException e) {
						System.out.println("Could not find Host");
					} catch (IOException e) {
						// TODO e.printStackTrace();
					}
				}else if(!con[0].equals("exit")){
					System.out.println("Unknown Command");
				}
			}
		}
		d.close();
	}
}
