import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.naming.OperationNotSupportedException;

/**
 * Implements Websocket behavior.
 * Pushes all text messages to DNChat, ignores binary messages.
 * Handles ping and close messages correctly, but does not react to pong messages.
 * Can send out messages, to do so add String to ConcurrentLinkedQueue.
 * The socket guarantees in order deliverance of messages, but not necessarily immediately.
 * 
 * TODO:
 * - Using int for ID limits number of users ever connected - not necessarily still connected -
 *  	to Server to int range.
 *
 */
public class Websocket {
	
	public enum State{
		CONNECTING,
		OPEN,
		CLOSING,
		CLOSED
	}
	
	private State readyState;
	private DataOutputStream out;
	private DataInputStream in;
	private int ID;
	private static int idCounter=0;
	private Date lastPong;
	private Thread pingThread;
	private final double TIMEOUT_SEC=30;
	private final double TIME_SLEEP_SEC=1;
	private boolean shouldMask=false;
	
	/**
	 * Edited by 1 DNChat Object, Read by 1 this (concurrently)
	 */
	public ConcurrentLinkedQueue<String> outBuffer=new ConcurrentLinkedQueue<String>();
	/**
	 * Edited by 1 DNChat Object, Read by 1 this (concurrently)
	 */	
	public AtomicBoolean doClose=new AtomicBoolean(false);
	
	

	/**
	 * Initializes a Websocket over the given input and output stream
	 * Handshake has already been performed, Server already 101 Switch Protocol
	 * @param inStream
	 * @param outStream
	 */
	public Websocket(DataInputStream inStream, DataOutputStream outStream, boolean mask){
		readyState=State.OPEN;
		in=inStream;
		out=outStream;
		ID=idCounter;
		idCounter++;
		setMask(mask);
		Lobby.dnChat.addClient(this);
		pingThread=new Thread(){
			public void run(){
				while(!this.isInterrupted()){
					sendPing();
					try{
						sleep((long) (1000*TIME_SLEEP_SEC));
					}catch(InterruptedException e){
						this.interrupt();
					}
				}
			}
		};
	}

	
	/**
	 * Checks regulary for incoming messages, sends them to DNChat
	 * Checks for outgoing messages, sends them to outputStream (Client)
	 * Throws exception if incoming binary or pong messages, as this is neither expected nor supported
	 * @throws OperationNotSupportedException 
	 */
	public void doYourJob() throws OperationNotSupportedException{
		while(readyState==State.OPEN){
			try {
				if(in.available()>0){
					WebsocketMessage msg=null;
					if(shouldMask){
						msg=getMessageAsClient();
					}else{
						msg=getMessage();
					}
					switch(msg.getType()){
					case TEXT:
						if(shouldMask){
							Lobby.dnChat.pushMessage(this, msg.getData());
						}else{
							Lobby.dnChat.pushMessage(this, msg.getDecodedData());
						}
						break;
					case PING:
						handlePing(msg);
						break;
					case CLOSE:						
						handleClose(msg);
						break;
					case PONG:
						handlePong(msg);
						break;
					default:
						throw new OperationNotSupportedException();					
					}
				}
				if(doClose.get()){
					closeConnection(1001);
				}
			} catch (IOException e) {
				// TODO e.printStackTrace();
			}
		}
	}
	
	/**
	 * Parses a message to a WebsocketMessage object, does not handle message.
	 * Closes connection, if a) message is fragmented or b) message is not masked
	 * @return
	 */
	public WebsocketMessage getMessage(){
		try {
			Byte b=in.readByte();
			WebsocketMessage.MessageType type=WebsocketMessage.MessageType.ELSE;
			int payloadSize1=0;
			int payloadSize2=0;
			BigInteger payloadSize3=BigInteger.ZERO;
			//Command Type
			byte opcode=getOpcode(b);
			if(!isSetBit(b,7) || (0x0)==opcode){
				//TODO: Close Connection and give response because fragmented Websocket message
				//TODO: check reason for error.
				closeConnection(0);
			}
			if(((byte)0x1)==opcode){
				type=WebsocketMessage.MessageType.TEXT;
			}
			if(((byte)0x2)==opcode){
				type=WebsocketMessage.MessageType.BINARY;
			}
			if(((byte)0x8)==opcode){
				type=WebsocketMessage.MessageType.CLOSE;
			}
			if(((byte)0x9)==opcode){
				type=WebsocketMessage.MessageType.PING;			
			}
			if(((byte)0xA)==opcode){
				type=WebsocketMessage.MessageType.PONG;				
			}
			b=in.readByte();
			if(!isSetBit(b, 7)){
				//TODO: Check reason.
				closeConnection(0);
			}
			//Read in payloadSize
			
			

			
			b=(byte) (b^(byte)-128);
			payloadSize1=Integer.valueOf(b);


			byte[] bytes=new byte[4];

			if(payloadSize1==126){
				b=in.readByte();
				byte b2=in.readByte();
				bytes[0]=0;
				bytes[1]=0;
				bytes[2]=b;
				bytes[3]=b2;
			}
			
			payloadSize2=new BigInteger(bytes).intValue();
			
			bytes=new byte[9];
			byte b2, b3, b4;
			if(payloadSize1==127){
				//bytes=new byte[9];
				b=in.readByte();
				b2=in.readByte();
				b3=in.readByte();
				b4=in.readByte();
				bytes[0]=0; //No Sign
				bytes[1]=b;
				bytes[2]=b2;
				bytes[3]=b3;
				bytes[4]=b4;
				b=in.readByte();
				b2=in.readByte();
				b3=in.readByte();
				b4=in.readByte();
				bytes[5]=b;
				bytes[6]=b2;
				bytes[7]=b3;
				bytes[8]=b4;
			}

			payloadSize3=new BigInteger(bytes);
			
			//Masking Key
			
			b=in.readByte();
			b2=in.readByte();
			b3=in.readByte();
			b4=in.readByte();
			bytes=new byte[]{b,b2,b3,b4};
				//Convert 2 Bytes to short
			WebsocketMessage msg=new WebsocketMessage(type, payloadSize1, payloadSize2, payloadSize3, bytes, in);
			return msg;
			
		} catch (IOException e) {
			// TODO e.printStackTrace();
			return null;
		}
		
	}
	
	/**
	 * Parses a message to a WebsocketMessage object, does not handle message.
	 * Closes connection, if a) message is fragmented or b) message is masked
	 * @return
	 */
	public WebsocketMessage getMessageAsClient(){
		try {
			Byte b=in.readByte();
			WebsocketMessage.MessageType type=WebsocketMessage.MessageType.ELSE;
			int payloadSize1=0;
			int payloadSize2=0;
			BigInteger payloadSize3=BigInteger.ZERO;
			//Command Type
			byte opcode=getOpcode(b);
			if(!isSetBit(b,7) || (0x0)==opcode){
				//TODO: Close Connection and give response because fragmented Websocket message
				//TODO: check reason for error.
				//System.out.println(!isSetBit(b,7));
				//System.out.println(b);
				closeConnection(0);
			}
			if(((byte)0x1)==opcode){
				type=WebsocketMessage.MessageType.TEXT;
			}
			if(((byte)0x2)==opcode){
				type=WebsocketMessage.MessageType.BINARY;
			}
			if(((byte)0x8)==opcode){
				//System.out.println("CLOSE!!");
				type=WebsocketMessage.MessageType.CLOSE;
			}
			if(((byte)0x9)==opcode){
				type=WebsocketMessage.MessageType.PING;			
			}
			if(((byte)0xA)==opcode){
				type=WebsocketMessage.MessageType.PONG;				
			}
			b=in.readByte();
			if(isSetBit(b, 7)){
				//TODO: Check reason.
				//System.out.println(isSetBit(b,7));
				//System.out.println(b);
				closeConnection(0);
			}
			//Read in payloadSize
			
			

			
			
			payloadSize1=Integer.valueOf(b);


			byte[] bytes=new byte[4];

			if(payloadSize1==126){
				b=in.readByte();
				byte b2=in.readByte();
				bytes[0]=0;
				bytes[1]=0;
				bytes[2]=b;
				bytes[3]=b2;
			}
			
			payloadSize2=new BigInteger(bytes).intValue();
			
			bytes=new byte[9];
			byte b2, b3, b4;
			if(payloadSize1==127){
				//bytes=new byte[9];
				b=in.readByte();
				b2=in.readByte();
				b3=in.readByte();
				b4=in.readByte();
				bytes[0]=0; //No Sign
				bytes[1]=b;
				bytes[2]=b2;
				bytes[3]=b3;
				bytes[4]=b4;
				b=in.readByte();
				b2=in.readByte();
				b3=in.readByte();
				b4=in.readByte();
				bytes[5]=b;
				bytes[6]=b2;
				bytes[7]=b3;
				bytes[8]=b4;
			}

			payloadSize3=new BigInteger(bytes);
			
				//Convert 2 Bytes to short
			WebsocketMessage msg=new WebsocketMessage(type, payloadSize1, payloadSize2, payloadSize3, bytes, in);
			return msg;
			
		} catch (IOException e) {
			// TODO e.printStackTrace();
			return null;
		}
		
	}
	
	
	/*
	 * HELPER METHOD
	 * Checks if Bit Nr. Position is set in byte b
	 */
	private boolean isSetBit(byte b,int position)
	{
	   return (byte) ((b >> position) & 0b00000001)==0b00000001;
	}

	/**
	 * Returns the unique ID of this Socket
	 * @return
	 */
	public int getID() {
		return ID;
	}

	/**
	 * Sends the String content over the socket.
	 * The size of the Textmessage is limited to Integer.MAX_VALUE
	 * @param content
	 */
	public void sendText(String content){
		//System.out.println("SENT TO CLIENT: "+content);
		sendMessage(content, 1);
	}
	/**
	 * Sends the content masked to a server
	 * @param content
	 */
	public void sendTextAsClient(String content){
		//System.out.println("SENT TO SERVER: "+content);
		sendMessageAsClient(content, 1);
	}
	
	/**
	 * Sends the String content with given opcode over the socket.
	 * The size of the Textmessage is limited to Integer.MAX_VALUE
	 * @param content
	 * @param opcode
	 */
	private void sendMessage(String content, int opcode){
		byte[] data=content.getBytes();
		byte first=(byte) (((byte)0b10000000)|(byte)opcode);
		byte[] payload;
		if(data.length<126){
			payload=new byte[1];
			payload[0]=(byte)data.length;
		}else if(data.length<(Math.pow(2, 24) -1)){
			payload=new byte[3];
			payload[0]=(byte) 126;
			ByteBuffer buf=ByteBuffer.allocate(4);
			buf.order(ByteOrder.BIG_ENDIAN);
			byte[] rest=buf.putInt(data.length).array();
			payload[1]=rest[2];
			payload[2]=rest[3];
		}else{
			payload=new byte[5];
			payload[0]=(byte) 127;
			ByteBuffer buf=ByteBuffer.allocate(4);
			buf.order(ByteOrder.BIG_ENDIAN);
			byte[] rest=buf.putInt(data.length-127).array();
			payload[1]=rest[0];
			payload[2]=rest[1];
			payload[3]=rest[2];
			payload[4]=rest[3];
		}
		byte[] msg=new byte[1+payload.length+data.length];
		payload[0]=(byte) (((byte)0b01111111)&payload[0]);
		msg[0]=first;
		int i;
		for(i=1; i<=payload.length; i++){
			msg[i]=payload[i-1];
		}
		for(int j=0; j<data.length; j++){
			msg[i+j]=data[j];
		}
		try {
			if(connectionIsDead()){
				return;
			}
			out.write(msg, 0, msg.length);
		} catch (IOException e) {
			//TODO: e.printStackTrace();
			connectionIsDead();
		}
	}
	/**
	 * Sends a masked Message
	 * @param content
	 * @param opcode
	 */
	private void sendMessageAsClient(String content, int opcode){
		byte[] data=content.getBytes();
		byte first=(byte) (((byte)0b10000000)|(byte)opcode);
		byte[] payload;
		if(data.length<126){
			payload=new byte[1];
			payload[0]=(byte)data.length;
		}else if(data.length<(Math.pow(2, 24) -1)){
			payload=new byte[3];
			payload[0]=(byte) 126;
			ByteBuffer buf=ByteBuffer.allocate(4);
			buf.order(ByteOrder.BIG_ENDIAN);
			byte[] rest=buf.putInt(data.length).array();
			payload[1]=rest[2];
			payload[2]=rest[3];
		}else{
			//TODO: Bug here!
			payload=new byte[5];
			payload[0]=(byte) 127;
			ByteBuffer buf=ByteBuffer.allocate(4);
			buf.order(ByteOrder.BIG_ENDIAN);
			byte[] rest=buf.putInt(data.length-127).array();
			payload[1]=rest[0];
			payload[2]=rest[1];
			payload[3]=rest[2];
			payload[4]=rest[3];
		}
		byte[]mask=new byte[4];
		Random r=new Random();
		r.nextBytes(mask);
		byte[] msg=new byte[1+payload.length+data.length+mask.length];
		payload[0]=(byte) (((byte)0b10000000)|payload[0]);
		msg[0]=first;
		int i;
		for(i=1; i<=payload.length; i++){
			msg[i]=payload[i-1];
		}
		int k;
		for(k=0; k<mask.length; k++){
			msg[i+k]=mask[k];
		}
		data=getEncodedBytes(data, mask);
		for(int j=0; j<data.length; j++){
			msg[i+k+j]=data[j];
		}
		try {
			if(connectionIsDead()){
				return;
			}
			out.write(msg, 0, msg.length);
		} catch (IOException e) {
			//TODO: e.printStackTrace();
			connectionIsDead();
		}
	}
	/**
	 * Checks if the connection to a server is still alive and if not does the necessary things
	 * @return
	 */
	private boolean connectionIsDead(){
		if(lastPong != null && new Date().getTime()-this.lastPong.getTime()>1000*TIMEOUT_SEC){
			Lobby.dnChat.reportServerDown(this);
			this.doClose.set(true);
			return true;
		}
		return false;
	}
	/**
	 * encodes/decodes byte to the mask
	 * @param toEncode
	 * @param maskingKey
	 * @return
	 */
	private byte[] getEncodedBytes(byte[] toEncode, byte[] maskingKey){
		byte[] encoded=new byte[toEncode.length];
		for (int i=0; i < toEncode.length; i++) {
		    encoded[i] = (byte) (toEncode[i] ^ maskingKey[i % 4]);
		}
		return encoded;
	}
	
	/**
	 * helper method that response to a ping with a pong
	 * @param msg
	 */
	private void handlePing(WebsocketMessage msg){
		if(msg.getType()!=WebsocketMessage.MessageType.PING){
			throw new IllegalArgumentException();
		}
		if(shouldMask){
			sendMessageAsClient(msg.getData(), 10);
		}else{
			sendMessage(msg.getDecodedData(), 10);
		}
	}
	/**
	 * sets the last pong flag
	 * @param msg
	 */
	private void handlePong(WebsocketMessage msg){
		if(msg.getType()!=WebsocketMessage.MessageType.PONG){
			throw new IllegalArgumentException();
		}
		if(shouldMask){
			msg.getData();
		}else{
			msg.getDecodedData();
		}
		lastPong=new Date();
	}
	/**
	 * Sends a ping message
	 */
	private void sendPing(){
		if(shouldMask){
			sendMessageAsClient("PING", 9);
		}else{
			sendMessage("PING", 9);
		}
	}
	
	/**
	 * helper method to handle client side initiated close
	 * @param msg
	 */
	private void handleClose(WebsocketMessage msg){
		if(msg.getType()!=WebsocketMessage.MessageType.CLOSE){
			throw new IllegalArgumentException();
		}
		// TODO richtige Stelle?
		Lobby.dnChat.removeClient(this);
		closeConnection(1000);
	}
	/**
	 * helper method to close connection
	 * @param reason
	 */
	private void closeConnection(int reason){
		this.stopToPing();
		if(shouldMask){
			synchronized(DNChat.class){
				for (Iterator<User> iterator = Lobby.dnChat.getUsers().values().iterator(); iterator.hasNext();) {
					User u = (User) iterator.next();
					if(!u.isServer())
						sendTextAsClient("LEFT "+u.getChatId());				
				}
				for (Iterator<User> iterator = Lobby.dnChat.getFarUsers().iterator(); iterator.hasNext();) {
					User u = (User) iterator.next();
					sendTextAsClient("LEFT "+u.getChatId());						
				}
				sendMessageAsClient(String.valueOf(reason), 8);
			}
		}else{
			synchronized(DNChat.class){
				for (Iterator<User> iterator = Lobby.dnChat.getUsers().values().iterator(); iterator.hasNext();) {
					User u = (User) iterator.next();
					if(!u.isServer())
						sendText("LEFT "+u.getChatId());				
				}
				for (Iterator<User> iterator = Lobby.dnChat.getFarUsers().iterator(); iterator.hasNext();) {
					User u = (User) iterator.next();
					sendText("LEFT "+u.getChatId());						
				}
			}
			sendMessage(String.valueOf(reason),8);
		}
		readyState=State.CLOSING;

	}
	/**
	 * helper method to get opcode out of byte from message
	 * @param b
	 * @return
	 */
	private byte getOpcode(byte b){
		return (byte) (((byte)0b00001111)&b);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Websocket) {
			Websocket s = (Websocket) obj;
			if (s.getID() == getID()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return "Websocket [ID=" + ID + "]";
	}
	/**
	 * Starts to ping regulary
	 */
	public void startToPing(){
		if(Lobby.dnChat.SENSE_MODE==false){
			return;
		}
		pingThread.start();
	}
	/**
	 * stops the regular pings (called if connection is dead)
	 */
	public void stopToPing(){
		if(Lobby.dnChat.SENSE_MODE==false){
			return;
		}
		if(pingThread.isAlive()){
			pingThread.interrupt();
		}
	}
	/**
	 * says if this server should send masked messages over this connection
	 * @return
	 */
	public boolean shouldMask(){
		return shouldMask;
	}
	/**
	 * sets the mask
	 * @param b
	 */
	public void setMask(boolean b){
		shouldMask=b;
	}
	
}
