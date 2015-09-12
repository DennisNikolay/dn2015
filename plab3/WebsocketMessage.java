import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

/**
 * Class Representing a Websocket Message.
 *
 */
public class WebsocketMessage {

	public enum MessageType{
		CONTINUE,
		TEXT,
		BINARY,
		CLOSE,
		PING,
		PONG,
		ELSE
	}
	
	private MessageType type;
	private int payload1;
	private int payload2;
	private BigInteger payload3;
	private byte[] maskingKey;
	private DataInputStream payloadData;
	
	
	public WebsocketMessage(MessageType t, int p1, int p2, BigInteger p3, byte[] mKey, DataInputStream in){
		type=t;
		payload1=p1;
		payload2=p2;
		payload3=p3;
		maskingKey=mKey;
		payloadData=in;
	}
	
	public MessageType getType(){
		return type;
	}
	
	public BigInteger getPayloadSize(){
		if(payload1<=125){
			return BigInteger.valueOf(payload1);
		}
		if(payload1==126){
			return BigInteger.valueOf(payload2);
		}
		if(payload1==127){
			return payload3.add(BigInteger.valueOf((long)127));
		}
		return null;
	}
	/**
	 * Reads data from input stream and decodes.
	 * Read is destructive, so data read once can not be read again.
	 * @return
	 */
	public String getDecodedData(){
		byte[] bytes=new byte[4];
		String result="";
		int mod4=0;
		BigInteger payloadsizer=getPayloadSize();
		for(BigInteger i=BigInteger.ZERO; i.compareTo(payloadsizer)<0; i=i.add(BigInteger.ONE)){
			mod4=i.mod(BigInteger.valueOf((long)4)).intValue();
			try {
				bytes[mod4]=payloadData.readByte();
				if(mod4==3){
					bytes=getDecodedBytes(bytes);
					result+=new String(bytes, "UTF-8");
					bytes=new byte[4];
				}
			} catch (IOException e) {
				// TODO e.printStackTrace();
			}
		}
		int payloadSizeMod4=getPayloadSize().mod(BigInteger.valueOf((long)4)).intValue();
		if(payloadSizeMod4!=0){
			byte[] bytes2=new byte[payloadSizeMod4];
			for(int i=payloadSizeMod4; i>0; i--){
				bytes2[payloadSizeMod4-i]=bytes[payloadSizeMod4-i];
			}
			try {
				result+=new String(getDecodedBytes(bytes2), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				// TODO e.printStackTrace();
			}
		}
		return result;
	}
	
	/**
	 * Gets the raw data without unmasking
	 */
	public String getData(){
		byte[] bytes=new byte[4];
		String result="";
		int mod4=0;
		BigInteger payloadsizer=getPayloadSize();
		for(BigInteger i=BigInteger.ZERO; i.compareTo(payloadsizer)<0; i=i.add(BigInteger.ONE)){
			mod4=i.mod(BigInteger.valueOf((long)4)).intValue();
			try {
				bytes[mod4]=payloadData.readByte();
				if(mod4==3){
					bytes=getDecodedBytes(bytes);
					result+=new String(bytes, "UTF-8");
					bytes=new byte[4];
				}
			} catch (IOException e) {
				// TODO e.printStackTrace();
			}
		}
		int payloadSizeMod4=getPayloadSize().mod(BigInteger.valueOf((long)4)).intValue();
		if(payloadSizeMod4!=0){
			byte[] bytes2=new byte[payloadSizeMod4];
			for(int i=payloadSizeMod4; i>0; i--){
				bytes2[payloadSizeMod4-i]=bytes[payloadSizeMod4-i];
			}
			try {
				result+=new String(getDecodedBytes(bytes2), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				// TODO e.printStackTrace();
			}
		}
		return result;
	}
	
	/**
	 * helper method that decodes bytes with masking key.
	 * @param encoded
	 * @return
	 */
	private byte[] getDecodedBytes(byte[] encoded){
		byte[] decoded=new byte[encoded.length];
		for (int i=0; i < encoded.length; i++) {
		    decoded[i] = (byte) (encoded[i] ^ maskingKey[i % 4]);
		}
		return decoded;
	}
}
