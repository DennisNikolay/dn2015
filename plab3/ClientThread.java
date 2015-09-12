import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
/**
 * All part of JDK
 */
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import javax.naming.OperationNotSupportedException;


/**
 * Thread started for each Client in protocol.
 * Performs Handshake and then switches to Websocket protocol.
 * Also closes the connection after returning from Websocket.
 * 
 *
 */
public class ClientThread extends Thread {

	private DataOutputStream out;
	private DataInputStream in;
    private final String GUID="258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private boolean doAgain=true;
    private Socket socket;
    private final int SLEEP_CLOSE_SEC=5;
    
    /**
     * Constructor for ClientThread
     * @param outStream - the stream over which data should be send out
     * @param inReader - the stream over which data should be read in
     * @param socket - the underlying tcp-socket
     */
	public ClientThread(DataOutputStream outStream, DataInputStream inReader, Socket socket){
		super();
		out=outStream;
		in=inReader;
		this.socket=socket;
	}
	/**
	 * "Main" of ClientThread.
	 * Try's to perform handshake, if success, switch to Websocket protocol
	 * otherwise try again.
	 * After performingHandshake successfull and returning from Websocket class,
	 * the connection is closed via method closeConnection.
	 */
	@Override
	public void run(){
		while(doAgain){
			performHandshake();
		}
		try {
			sleep(SLEEP_CLOSE_SEC*1000);
		} catch (InterruptedException e) {
			// TODO e.printStackTrace();
		}
		closeConnection();
	}
	
	/**
	 * Try to parse incoming TCP-Message, if expected, switch to Websocket protocol,
	 * else give bad response.
	 */
	private void performHandshake(){
		Request r=parseRequest();
		if(r==null || r.getRequest()==null){
			return;
		}
		if(r.getRequest()!=Request.RequestToken.GET){
			giveBadResponse("Not GET");
		}else{
			HashMap<Request.HeaderToken, String> map=r.getHeader();
			String host=map.get(Request.HeaderToken.Host);
			String upgrade=map.get(Request.HeaderToken.Upgrade);
			String connection=map.get(Request.HeaderToken.Connection);
			String secWebsocketKey=map.get(Request.HeaderToken.SecWebsocketKey);
			//String secWebsocketProtocol=map.get(Request.HeaderToken.SecWebsocketProtocol);
			String secWebsocketVersion=map.get(Request.HeaderToken.SecWebsocketVersion);
			if(host==null || upgrade==null || connection==null || secWebsocketKey==null || secWebsocketVersion==null){
				giveBadResponse("Header Missing");
				return;
			}
			//TODO: Check Host!
			/*if(!host.toLowerCase().contains(originSocket.getInetAddress().toString().toLowerCase())){
				giveBadResponse("Wrong Host - Expected: "+originSocket.getInetAddress());
				return;
			}*/
			if(!upgrade.toLowerCase().contains("websocket")){
				giveBadResponse("Not websocket");
				return;
			}
			if(!connection.toLowerCase().contains("upgrade")){
				giveBadResponse("Not upgrade");
				return;
			}
			if(Base64.decode(secWebsocketKey).length!=16){
				giveBadResponse("Incorrect Key");
				return;
			}
			if(Integer.valueOf(secWebsocketVersion)!=13){
				giveBadResponse("Wrong Websocket Version");
				return;
			}
			
			try {
				MessageDigest md=MessageDigest.getInstance("SHA-1");
				String s=secWebsocketKey+GUID;
				/*System.out.println("HTTP/1.1 101 Switching Protocols \r\n" +
						"Upgrade: websocket \r\n" +
						"Connection: Upgrade \r\n" +
						"Sec-WebSocket-Accept: " + Base64.encode(md.digest(s.getBytes()))
						+"\r\n\r\n");*/
				out.writeBytes("HTTP/1.1 101 Switching Protocols \r\n" +
						"Upgrade: websocket \r\n" +
						"Connection: Upgrade \r\n" +
						"Sec-WebSocket-Accept: " + Base64.encode(md.digest(s.getBytes()))
						+"\r\n\r\n");
				Websocket webSocket=new Websocket(in, out, false);
				try {
					webSocket.doYourJob();
					doAgain=false;
				} catch (OperationNotSupportedException e) {
					//TODO: e.printStackTrace();
				}
			} catch (IOException e) {
				//TODO: e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				//TODO: e.printStackTrace();
			}
			
			
		}
	}
	/**
	 * Helper Method to give 400 Bad Request Message
	 * @param reason - human readable string 
	 */
	private void giveBadResponse(String reason){
		try {
			//System.out.println("HTTP/1.1 400 Bad Request, because "+reason+"\r\n");
			out.writeBytes("HTTP/1.1 400 Bad Request, because "+reason+"\r\n");
		} catch (IOException e) {
			//TODO: e.printStackTrace();
		}
	}
	
	
	/**
	 * helper method to parse request (not really but as needed to switch protocol)
	 * @return the parsed request as a Request Object
	 */
	private Request parseRequest(){
		Request.RequestToken req=Request.RequestToken.INVD;
		float version=0;
		String location="";
		HashMap<Request.HeaderToken, String> head=new HashMap<Request.HeaderToken, String>();
		BufferedReader readIn=new BufferedReader(new InputStreamReader(in));
		try{
			String line=readIn.readLine();
			String msg[];
			if(line==null||line.isEmpty()){
				return null;
			}
			msg=line.split(" ");
			switch(msg[0]){
			case "GET":
				req=Request.RequestToken.GET;
				location=msg[1];
				String[] msgSplit=msg[2].split("/");
				version=Float.valueOf(msgSplit[1]);
				break;
			}
			while(line!=null){
				//System.out.println(line);
				if(line==null||line.isEmpty()){
					break;
				}
				msg=line.split(":");
				switch(msg[0]){
				case "Host":
					head.put(Request.HeaderToken.Host, msg[1]);
					break;
				case "Upgrade":
					head.put(Request.HeaderToken.Upgrade, msg[1]);
					break;
				case "Connection":
					head.put(Request.HeaderToken.Connection, msg[1]);
					break;
				case "Sec-WebSocket-Key":
					head.put(Request.HeaderToken.SecWebsocketKey, msg[1]);
					break;
				case "Sec-WebSocket-Protocol":
					head.put(Request.HeaderToken.SecWebsocketProtocol, msg[1]);
					break;
				case "Sec-WebSocket-Version":
					head.put(Request.HeaderToken.SecWebsocketVersion, msg[1]);
					break;
				case "Origin":
					head.put(Request.HeaderToken.Origin, msg[1]);
					break;
				default:
					//giveBadResponse("Did not understand");
					break;
				}	
			line=readIn.readLine();
			}
			return new Request(req, location, version, head);
		}catch(Exception e){
			giveBadResponse("");
			return null;
		}
	}
	/**
	 * Call to close TCP-connection. 
	 */
	public void closeConnection(){
		try{
			//in.close();
			socket.close();
			//out.close();
		}catch(Exception e){}
	}
	
}
