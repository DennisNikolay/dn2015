import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;


public class ConnectThread extends Thread {

	@Override
	public void run(){
		//FOR PL3
		Scanner d=new Scanner(System.in);
		d.useDelimiter("\n");
		while(true){
			if(d.hasNext()){
				String msg=d.next();
				String[] con=msg.split(" ");
				if(!con[0].equals("connect")  || con.length!=3){
					break;
				}
				try {
					Socket s=new Socket(con[1], Integer.valueOf(con[2]));
					new WebsocketThread(s).start();
				} catch (NumberFormatException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
			//FOR PL3 END
		}

	}
}
