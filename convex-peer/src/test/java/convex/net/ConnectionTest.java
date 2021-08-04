package convex.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.Test;

import convex.core.data.prim.CVMLong;
import convex.core.exceptions.BadFormatException;
import convex.core.store.Stores;
import convex.core.util.Utils;

public class ConnectionTest {
	
	@Test
	public void testMessageFlood() throws IOException, BadFormatException, InterruptedException {
		final ArrayList<Message> received = new ArrayList<>();

		// create a custom PeerConnection and MessageReceiver for testing
		// null Queue OK, we aren't queueing with our custom receive action
		MessageReceiver mr = new MessageReceiver(a -> received.add(a), null);
		
		MemoryByteChannel chan = MemoryByteChannel.create(1000);

		Connection conn=Connection.create(chan, null, Stores.current(), null);
		
		Thread receiveThread=new Thread(()-> {
			while (true) {
				try {
					mr.receiveFromChannel(chan);
					if(Thread.interrupted()) return;
				} catch (BadFormatException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					throw Utils.sneakyThrow(e);
				}
			}
		});
		receiveThread.start();
		
//		for (int i=0; i<10000; i++) {
//			boolean sent=false;
//			while(!sent) {
//				sent=conn.sendData(CVMLong.create(i));
//			}
//		}
//			
//		assertEquals(10000,received.size());
//		mr.receiveFromChannel(chan);
//		assertEquals(10000,received.size());
		
		receiveThread.interrupt();
		receiveThread.join();
	}

}
