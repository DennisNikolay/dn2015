

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

import org.junit.Test;


public class WebsocketTests {


	private byte[] inputStream = {0b0_000_0000, };
	private  Websocket s = new Websocket(new DataInputStream(new ByteArrayInputStream(inputStream)), new DataOutputStream(new ByteArrayOutputStream()), null);
	
	@Test
	public void test() {
		
		byte b = 0b000;
		
		
		assertTrue(2==2);
	}
	
	
	

}
