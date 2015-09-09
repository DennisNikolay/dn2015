import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Class representing a HTTP Request
 *
 */
public class Request {

	private RequestToken request;
	private float httpVersion;
	private String location;
	private HashMap<HeaderToken, String> header;
	
	public enum RequestToken{
		GET,
		INVD
	} 
	
	public enum HeaderToken{
		Host,
		Upgrade,
		Connection,
		SecWebsocketKey,
		SecWebsocketProtocol,
		SecWebsocketVersion,
		Origin
	}

	public Request(RequestToken r, String location, float version, HashMap<HeaderToken, String> headers){
		request=r;
		httpVersion=version;
		for(Entry<HeaderToken, String> e: headers.entrySet()){
			headers.put(e.getKey(), e.getValue().replace(" ", ""));
		}
		header=headers;
		
		
		this.location=location;
	}
	
	public RequestToken getRequest(){
		return request;
	}
	
	public float getHTTPVersion(){
		return httpVersion;
	}
	
	public HashMap<HeaderToken, String> getHeader(){
		return header;
	}
	
	public String getLocation(){
		return location;
	}
	
}
