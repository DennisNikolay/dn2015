import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import javax.naming.OperationNotSupportedException;

import com.sun.org.apache.xml.internal.security.utils.Base64;


public class WebsocketThread extends Thread {

	private Socket socket;
	
	public WebsocketThread(Socket s){
		socket=s;
	}
	
	@Override
	public void run(){
		try {
			Random r=new Random();
			byte[] bytes=new byte[16];
			r.nextBytes(bytes);
			String data="GET / HTTP/1.1\r\n" +
					"Host: localhost:42015\r\n" +
					"Sec-WebSocket-Version: 13\r\n" +
					"Origin: null\r\n" +
					"Sec-WebSocket-Key: " +Base64.encode(bytes)+"\r\n"+
					"Connection: keep-alive, Upgrade\r\n" +
					"Upgrade: websocket\r\n\r\n";
			DataOutputStream out=new DataOutputStream(socket.getOutputStream());
			out.write(data.getBytes(StandardCharsets.UTF_8));
			DataInputStream in=new DataInputStream(socket.getInputStream());
			BufferedReader readIn=new BufferedReader(new InputStreamReader(in));
			String line=readIn.readLine();
			while(line!=null && !line.isEmpty()){
				line=readIn.readLine();
			}
			Websocket websocket=new Websocket(in, new DataOutputStream(socket.getOutputStream()), true);
			String number=toSrvrNumber(Lobby.dnChat.getPassword());
			websocket.sendTextAsClient("SRVR "+number);
			Lobby.dnChat.setServer(websocket);
			synchronized(DNChat.class){
				Lobby.dnChat.propagateArrivals(Lobby.dnChat.getUsers().get(websocket.getID()));
			}
			websocket.doYourJob();
			socket.close();
		} catch (IOException | OperationNotSupportedException e) {
			// e.printStackTrace();
		}
		
	}

	private String toSrvrNumber(String password) { 
		return "63383060908028598";
	}
}
