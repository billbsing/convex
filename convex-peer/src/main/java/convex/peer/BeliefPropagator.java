package convex.peer;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import convex.core.Belief;
import convex.core.data.ACell;
import convex.core.data.Ref;
import convex.core.store.Stores;
import convex.core.util.Utils;
import convex.net.message.Message;

/**
 * Component class to handle propagation of new Beliefs from a Peer
 */
public class BeliefPropagator {
	
	public static final int MIN_BELIEF_BROADCAST_DELAY=50;
	public static final int BELIEF_REBROADCAST_DELAY=2000;
	private static final int BELIEF_PROPAGATOR_QUEUE_SIZE = 10;

	protected final Server server;
	
	private ArrayBlockingQueue<Belief> beliefQueue=new ArrayBlockingQueue<>(BELIEF_PROPAGATOR_QUEUE_SIZE);
	
	static final Logger log = LoggerFactory.getLogger(BeliefPropagator.class.getName());

	public BeliefPropagator(Server server) {
		this.server=server;
	}
	
	protected final Runnable beliefPropagatorLoop = new Runnable() {
		@Override
		public void run() {
			Stores.setCurrent(server.getStore());
			while (server.isLive()) {
				try {
					// wait until the thread is notified of new work
					Belief b=beliefQueue.poll(1000, TimeUnit.MILLISECONDS);
					if (b!=null) {
						doBroadcastBelief(b);
					}
					
				
				} catch (InterruptedException e) {
					log.trace("Belief Propagator thread interrupted on "+server);
				} catch (Throwable e) {
					log.warn("Unexpected exception in Belief propagator: ",e);
				}
			}
		}
	};
	
	public boolean isBroadcastDue() {
		return (lastBroadcastTime+MIN_BELIEF_BROADCAST_DELAY)<Utils.getCurrentTimestamp();
	}
	
	protected final Thread beliefPropagatorThread=new Thread(beliefPropagatorLoop);
	
	/**
	 * Time of last belief broadcast
	 */
	long lastBroadcastTime=0;
	private long beliefBroadcastCount=0L;
	
	public long getBeliefBroadcastCount() {
		return beliefBroadcastCount;
	}
	
	public synchronized boolean queueBelief(Belief belief) {
		return beliefQueue.offer(belief);
	}
	
	private void doBroadcastBelief(Belief belief) {
		if (belief==null) {
			log.warn("Unexpected null Belief!!");
			return;
		}

		ArrayList<ACell> novelty=new ArrayList<>();
		
		// At this point we know something updated our belief, so we want to rebroadcast
		// belief to network
		Consumer<Ref<ACell>> noveltyHandler = r -> {
			ACell o = r.getValue();
			novelty.add(o);
		};

		// persist the state of the Peer, announcing the new Belief
		// (ensure we can handle missing data requests etc.)
		belief=ACell.createAnnounced(belief, noveltyHandler);

		Message msg = Message.createBelief(belief, novelty);
		server.manager.broadcast(msg, false);
		
		lastBroadcastTime=Utils.getCurrentTimestamp();
		beliefBroadcastCount++;
	}

	public void close() {
		beliefPropagatorThread.interrupt();
	}

	public void start() {
		beliefPropagatorThread.start();
	}
}
