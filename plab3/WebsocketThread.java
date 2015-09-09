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
			String data="GET / HTTP/1.1\n" +
					"Host: localhost:42015\n" +
					"Sec-WebSocket-Version: 13\n" +
					"Origin: null\n" +
					"Sec-WebSocket-Key: " +Base64.encode(bytes)+"\n"+
					"Connection: keep-alive, Upgrade\n" +
					"Upgrade: websocket\n\n";
			DataOutputStream out=new DataOutputStream(socket.getOutputStream());
			out.write(data.getBytes(StandardCharsets.UTF_8));
			DataInputStream in=new DataInputStream(socket.getInputStream());
			BufferedReader readIn=new BufferedReader(new InputStreamReader(in));
			String line=readIn.readLine();
			while(line!=null && !line.isEmpty()){
				line=readIn.readLine();
			}
			Websocket websocket=new Websocket(in, new DataOutputStream(socket.getOutputStream()));
			websocket.sendTextAsClient("SRVR 0");
			Lobby.dnChat.setServer(websocket);
			websocket.doYourJob();
			socket.close();
		} catch (IOException | OperationNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
