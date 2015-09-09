import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.Scanner;

import com.sun.org.apache.xml.internal.security.utils.Base64;

/**
 * Class that represents Startpoint to all Clients. 
 * Incoming TCP connections get accepted and forwarded to own thread here.
 *
 */
public class Lobby {

	/*
	 * The global DNChat Object
	 */
	public static DNChat dnChat;
	
	private static int port=42015;
	/**
	 * Creates a global DNChat Object and assigns it to the static field dnChat,
	 * afterwards waits for incoming TCP-Connections.
	 * If a connection comes in a new thread is created.
	 * The main thread is active all the time. 
	 * @param args - No Arguments Expected
	 */
	public static void main(String[] args) {
		dnChat = new DNChat();
		Scanner d=new Scanner(System.in);
		d.useDelimiter("\n");
		while(!d.hasNext()){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		port=d.nextInt();
		try {
			ServerSocket socket = new ServerSocket(port);
			new ConnectThread().start();
			while(true){
				Socket connection=socket.accept();
				DataInputStream in=new DataInputStream(connection.getInputStream());
				DataOutputStream out=new DataOutputStream(connection.getOutputStream());
				new ClientThread(out, in, connection).start();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
