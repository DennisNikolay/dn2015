import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

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
	/*
	 * A Bool Flag indicating that the server shouldTerminate
	 */
	public static AtomicBoolean shouldTerminate=new AtomicBoolean(false);
	/**
	 * The Standard port of the server
	 */
	private static int port=42011;
	/**
	 * The Http-socket for handshakeing
	 *
	 */
	
	private static final boolean READ_IN_PORT=false;
	
	public static ServerSocket httpSocket;
	/**
	 * Creates a global DNChat Object and assigns it to the static field dnChat,
	 * afterwards waits for incoming TCP-Connections.
	 * If a connection comes in a new thread is created.
	 * The main thread is active all the time until terminated. 
	 * @param args - No Arguments Expected
	 */
	public static void main(String[] args) {
		dnChat = new DNChat();
		if(READ_IN_PORT){
			readPortFromCommandLine();
		}
		try {
			httpSocket = new ServerSocket(port);
			new ConnectThread().start();
			while(!shouldTerminate.get()){
				Socket connection=httpSocket.accept();
				DataInputStream in=new DataInputStream(connection.getInputStream());
				DataOutputStream out=new DataOutputStream(connection.getOutputStream());
				new ClientThread(out, in, connection).start();
			}
		} catch (IOException e) {
			System.out.println("Not valid");
			//TODO: e.printStackTrace();
		}	
	}
	/**
	 * Reads the port of this server from the command line
	 */
	private static void readPortFromCommandLine(){
		@SuppressWarnings("resource")
		Scanner d=new Scanner(System.in);
		d.useDelimiter("\n");
		while(!d.hasNext()){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO: e.printStackTrace();
			}
		}
		port=d.nextInt();
	}

}
