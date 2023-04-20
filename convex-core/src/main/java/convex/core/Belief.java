package convex.core;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import convex.core.crypto.AKeyPair;
import convex.core.data.ABlob;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.ARecord;
import convex.core.data.AVector;
import convex.core.data.AccountKey;
import convex.core.data.Blob;
import convex.core.data.BlobMap;
import convex.core.data.BlobMaps;
import convex.core.data.Format;
import convex.core.data.Hash;
import convex.core.data.IRefFunction;
import convex.core.data.Keywords;
import convex.core.data.MapEntry;
import convex.core.data.PeerStatus;
import convex.core.data.Ref;
import convex.core.data.SignedData;
import convex.core.data.Tag;
import convex.core.data.prim.CVMLong;
import convex.core.exceptions.BadFormatException;
import convex.core.exceptions.BadSignatureException;
import convex.core.exceptions.InvalidDataException;
import convex.core.lang.RT;
import convex.core.lang.impl.RecordFormat;
import convex.core.util.Counters;
import convex.core.util.Utils;

/**
 * Class representing a Peer's view of the overall network consensus state.
 * 
 * Belief is immutable, and is designed to be independent of any particular Peer
 * so that it can be efficiently merged towards consensus.
 * 
 * Belief can be merged with other Beliefs from the perspective of a Peer. This
 * property is fundamental to the Convex consensus algorithm.
 * 
 * "Sorry to be a wet blanket. Writing a description for this thing for general
 * audiences is bloody hard. There's nothing to relate it to." – Satoshi
 * Nakamoto
 */
public class Belief extends ARecord {
	private static final RecordFormat BELIEF_FORMAT = RecordFormat.of(Keywords.ORDERS, Keywords.TIMESTAMP);

	/**
	 * The latest view of signed Orders held by other Peers
	 */
	private final BlobMap<AccountKey,SignedData<Order>> orders;

	/**
	 * The timestamp at which this belief was created
	 */
	private final long timestamp;

	// private final long timeStamp;

	private Belief(BlobMap<AccountKey,SignedData<Order>> orders, long timestamp) {
		super(BELIEF_FORMAT.count());
		this.orders = orders;
		this.timestamp = timestamp;
	}

	@Override
	public ACell get(ACell k) {
		if (Keywords.ORDERS.equals(k)) return orders;
		if (Keywords.TIMESTAMP.equals(k)) return CVMLong.create(timestamp);
		return null;
	}

	@Override
	public Belief updateRefs(IRefFunction func) {
		BlobMap<AccountKey, SignedData<Order>> newOrders = Ref.updateRefs(orders, func);
		if (this.orders == newOrders) {
			return this;
		}
		return new Belief(newOrders, timestamp);
	}

	/**
	 * Gets an empty Belief
	 * @return Empty Belief
	 */
	public static Belief initial() {
		return create(BlobMaps.empty());
	}
	
	/**
	 * Create a Belief with a single order signed by the given key pair, using initial timestamp.
	 * @param kp Peer Key pair with which to sign the order.
	 * @param order Order of blocks that the Peer is proposing
	 * @return new Belief representing the isolated belief of a single Peer.
	 */
	public static Belief create(AKeyPair kp, Order order) {
		BlobMap<AccountKey, SignedData<Order>> orders=BlobMap.of(kp.getAccountKey(),kp.signData(order));
		return create(orders);
	}


	private static Belief create(BlobMap<AccountKey, SignedData<Order>> orders, long timestamp) {
		return new Belief(orders, timestamp);
	}
	
	@SuppressWarnings("unchecked")
	public static Belief create(SignedData<Order>... orders)  {
		BlobMap<AccountKey, SignedData<Order>> os=BlobMaps.empty();
		long timestamp=0;
		for (SignedData<Order> o:orders) {
			Order order=o.getValue();
			timestamp=Math.max(timestamp, order.getTimestamp());
			os=os.assoc(o.getAccountKey(), o);
		}
		return new Belief(os,timestamp);
	}
	

	public static Belief create(HashMap<AccountKey, SignedData<Order>> orderMap, long timestamp) {
		BlobMap<AccountKey, SignedData<Order>> orders=BlobMaps.create(orderMap);
		return new Belief(orders, timestamp);
	}

	private static Belief create(BlobMap<AccountKey, SignedData<Order>> orders) {
		return create(orders, Constants.INITIAL_TIMESTAMP);
	}

	/**
	 * Create a Belief with a single empty order. USeful for Peer startup.
	 * 
	 * @param kp Keypair for Peer
	 * @return New Belief
	 */
	public static Belief createSingleOrder(AKeyPair kp) {
		AccountKey address = kp.getAccountKey();
		SignedData<Order> order = kp.signData(Order.create());
		return create(BlobMap.of(address, order));
	}

	/**
	 * The Belief merge function
	 * 
	 * @param mc MergeContext for Belief Merge
	 * @param beliefs An array of Beliefs. May contain nulls, which will be ignored.
	 * @return The updated merged belief with latest timestamp, or the same Belief if there is no change to Orders.
	 * @throws BadSignatureException In case of a bad signature
	 * @throws InvalidDataException In case of invalid data
	 */
	public Belief merge(MergeContext mc, Belief... beliefs) throws BadSignatureException, InvalidDataException {
		Counters.beliefMerge++;

		// accumulate combined list of latest Orders for all peers
		final BlobMap<AccountKey, SignedData<Order>> accOrders = accumulateOrders(mc,beliefs);

		// vote for new proposed chain
		final BlobMap<AccountKey, SignedData<Order>> resultOrders = vote(mc, accOrders);
		if (resultOrders == null) return this;

		// update my belief with the resulting Orders
		long newTimestamp = mc.getTimestamp();
		if (orders == resultOrders) return this;
		final Belief result = new Belief(resultOrders, newTimestamp);

		return result;
	}

	/**
	 * Assemble the latest map of Orders from all peers by merging from each Belief received
	 * @param beliefs Set of Beliefs from which to merge orders
	 * @return
	 */
	private BlobMap<AccountKey, SignedData<Order>> accumulateOrders(MergeContext mc, Belief[] beliefs) {
		// Initialise result with existing Orders from this Belief
		BlobMap<AccountKey, SignedData<Order>> result = this.orders;
		
		// Iterate over each received Belief
		for (Belief belief : beliefs) {
			if (belief == null) continue; // ignore null Beliefs, might happen if invalidated
			
			result=accumulateOrders(mc, result, belief);
		}
		return result;
	}
	
	public Belief mergeOrders(MergeContext mc, Belief b) {
		BlobMap<AccountKey, SignedData<Order>> newOrders=accumulateOrders(mc, orders,b);
		return withOrders(newOrders);
	}
	
	/**
	 * Update a map of orders from all peers by merging from each Belief received
	 * @param belief Belief from which to merge orders
	 * @return Updated map of orders
	 */
	private BlobMap<AccountKey, SignedData<Order>> accumulateOrders(MergeContext mc, BlobMap<AccountKey, SignedData<Order>> orders,Belief belief) {
		BlobMap<AccountKey, SignedData<Order>> result=orders;
		
		BlobMap<AccountKey, SignedData<Order>> bOrders = belief.orders;
		// Iterate over each Peer's ordering conveyed in this Belief
		long bcount=bOrders.count();
		for (long i=0; i<bcount; i++) {
			MapEntry<AccountKey,SignedData<Order>> be=bOrders.entryAt(i);
			ABlob key=be.getKey();
			
			SignedData<Order> b=be.getValue();
			if (b == null) continue; // If there is no incoming Order skip, though shouldn't happen
			Order bc = b.getValue();
			if (bc.getTimestamp()>mc.getTimestamp()) continue; // ignore future Orders
			
			SignedData<Order> a=result.get(key);
			if (a == null) {
				// This is a new order to us, so include if valid
				result=result.assocEntry(be); 
				continue;
			}
			
			if (a.equals(b)) continue; // PERF: fast path for no changes

			Order ac = a.getValue();

			boolean shouldReplace=compareOrders(ac,bc);
			if (shouldReplace) {

				
				result=result.assocEntry(be); 
				continue;
			}
		}
		return result;
	}

	
	/**
	 * Checks if a new Order should replace the current order when collecting Peer orders
	 * @param oldOrder Current Order
	 * @param newOrder Potential new ORder
	 * @return
	 */
	public static boolean compareOrders(Order oldOrder, Order newOrder) {
		if (newOrder==null) return false;
		if (oldOrder==null) return true;
		
		int tsComp=Long.compare(oldOrder.getTimestamp(), newOrder.getTimestamp());
		if (tsComp>0) return false; // Keep current order if more recent
		
		if (tsComp<0) {
			// new Order is more recent, so switch to this
			return true;
		} else {
			// This probably shouldn't happen if peers are sticking to timestamps
			// But we compare anyway
			// Prefer advanced consensus
			if (newOrder.getConsensusPoint()>oldOrder.getConsensusPoint()) return true;
			
			// Then prefer advanced proposal
			if (newOrder.getProposalPoint()>oldOrder.getProposalPoint()) return true;

			// Finally prefer more blocks
			AVector<SignedData<Block>> abs=oldOrder.getBlocks();
			AVector<SignedData<Block>> bbs=newOrder.getBlocks();
			if(abs.count()<bbs.count()) return true;
		}
		return false;
	}

	/**
	 * Conducts a stake-weighted vote across a map of consistent chains, in the
	 * given merge context
	 * 
	 * @param accOrders Accumulated map for latest Orders received from all Peer Beliefs
	 * @param mc Merge context
	 * @param filteredChains
	 * @return
	 * @throws BadSignatureException @
	 */
	private BlobMap<AccountKey, SignedData<Order>> vote(final MergeContext mc, final BlobMap<AccountKey, SignedData<Order>> accOrders)
			throws BadSignatureException {
		AccountKey myAddress = mc.getAccountKey();

		// get current Order for this peer.
		final Order myOrder = getMyOrder(mc);
		assert (myOrder != null); // we should always have a Order!
		
		// get the Consensus state from this Peer's current perspective
		// this is needed for peer weights: we only trust peers who have stake in the
		// current consensus!
		State votingState = mc.getConsensusState();

		// filter Orders for compatibility with current Order for inclusion in Voting Set
		// TODO: figure out what to do with new blocks filtered out?
		final BlobMap<AccountKey, SignedData<Order>> filteredOrders = accOrders.filterValues(signedOrder -> {
			try {
				Order otherOrder = signedOrder.getValue();
				return myOrder.checkConsistent(otherOrder);
			} catch (Exception e) {
				throw Utils.sneakyThrow(e);
			}
		});

		// Current Consensus Point
		long consensusPoint = myOrder.getConsensusPoint();

		// Compute stake for all peers in consensus state
		AMap<AccountKey, PeerStatus> peers = votingState.getPeers();
		HashMap<AccountKey, Double> weightedStakes = votingState.computeStakes();
		double totalStake = weightedStakes.get(null);

		// Extract unique proposed chains from provided map, computing vote for each.
		// compute the total weighted vote at the same time in accumulator
		// Peers with no stake should be ignored (might be old peers etc.)
		HashMap<Order, Double> stakedOrders = new HashMap<>(peers.size());
		double consideredStake = prepareStakedOrders(filteredOrders, weightedStakes, stakedOrders);

		// Get the winning chain for this peer, including new blocks encountered
		AVector<SignedData<Block>> winningBlocks = computeWinningOrder(stakedOrders, consensusPoint, consideredStake);
		if (winningBlocks == null) return null; // if no voting stake on any chain

		// Take winning blocks into my Order
		// winning chain should have same consensus as my initial chain
		Order winningOrder = myOrder.withBlocks(winningBlocks);

		final double P_THRESHOLD = totalStake * Constants.PROPOSAL_THRESHOLD;
		final Order proposedOrder = updateProposal(winningOrder, stakedOrders, P_THRESHOLD);

		assert (proposedOrder != null);

		final double C_THRESHOLD = totalStake * Constants.CONSENSUS_THRESHOLD;
		final Order consensusOrder = updateConsensus(proposedOrder, stakedOrders, C_THRESHOLD);

		BlobMap<AccountKey, SignedData<Order>> resultOrders = filteredOrders;
		if (!consensusOrder.consensusEquals(myOrder)) {
			// We have a different Order to propose
			// First check how consistent this is with out current Order
			long match = consensusOrder.getBlocks().commonPrefixLength(myOrder.getBlocks());
			long ts=mc.getTimestamp();
			
			// We always want to replace our Order if consistent with our current proposal
			boolean shouldReplace=match>=myOrder.getProposalPoint();
			
			// If we need to switch proposals be careful!
			// We only do this after sufficient time has elapsed
			if (!shouldReplace) {
				// Replace if we observe a consensus elsewhere??
				//long newConensusPoint=consensusOrder.getConsensusPoint();
				//if (newConensusPoint>consensusPoint) {
				//	shouldReplace=true;
				//}
				
				long keepProposalTime=Constants.KEEP_PROPOSAL_TIME; // TODO: needs consideration, maybe randomise?
				if (mc.getTimestamp()>myOrder.getTimestamp()+keepProposalTime) {
					shouldReplace=true;
				}
			}
			
			if (shouldReplace) {
				// Update timestamp
				Order myNewOrder=consensusOrder.withTimestamp(ts);
			
				// Only sign and update Order if it has changed
				final SignedData<Order> signedOrder = mc.sign(myNewOrder);
				resultOrders = resultOrders.assoc(myAddress, signedOrder);
			}
		}
		return resultOrders;
	}

	/**
	 * Updates the consensus point for the winning Order, given an overall map of
	 * staked orders and consensus threshold.
	 */
	private Order updateConsensus(Order proposedOrder, HashMap<Order, Double> stakedOrders, double THRESHOLD) {
		AVector<SignedData<Block>> proposedBlocks = proposedOrder.getBlocks();
		ArrayList<Order> agreedChains = Utils.sortListBy(new Function<Order, Long>() {
			@Override
			public Long apply(Order c) {
				// scoring function scores by level of proposed agreement with proposed chain
				// in order to sort by length of matched proposals
				long blockMatch = proposedBlocks.commonPrefixLength(c.getBlocks());

				long minProposal = Math.min(proposedOrder.getProposalPoint(), c.getProposalPoint());

				long match = Math.min(blockMatch, minProposal);
				if (match <= proposedOrder.getConsensusPoint()) return null; // skip if no progress vs existing
																				// consensus
				return -match;
			}
		}, stakedOrders.keySet());
		int numAgreed = agreedChains.size();
		// assert(proposedChain.equals(agreedChains.get(0)));
		double accumulatedStake = 0.0;
		int i = 0;
		for (; i < numAgreed; i++) {
			Order c = agreedChains.get(i);
			Double chainStake = stakedOrders.get(c);
			accumulatedStake += chainStake;
			if (accumulatedStake > THRESHOLD) break;
		}

		if (i < numAgreed) {
			// we have a consensus!
			Order lastAgreed = agreedChains.get(i);
			long prefixMatch = proposedOrder.getBlocks().commonPrefixLength(lastAgreed.getBlocks());
			long proposalMatch = Math.min(proposedOrder.getProposalPoint(), lastAgreed.getProposalPoint());
			long newConsensusPoint = Math.min(prefixMatch, proposalMatch);
			if (newConsensusPoint < proposedOrder.getConsensusPoint()) {
				throw new Error("Consensus going backwards! prefix=" + prefixMatch + " propsalmatch=" + proposalMatch);
			}
			return proposedOrder.withConsenusPoint(newConsensusPoint);
		} else {
			return proposedOrder;
		}
	}

	/**
	 * Updates the proposal point for the winning Order, given an overall map of
	 * staked Orders and consensus threshold.
	 */
	private Order updateProposal(Order winningOrder, HashMap<Order, Double> stakedOrders, double THRESHOLD) {
		AVector<SignedData<Block>> winningBlocks = winningOrder.getBlocks();

		// sort all chains according to extent of agreement with winning chain
		ArrayList<Order> agreedOrders = sortByAgreement(stakedOrders, winningBlocks);
		int numAgreed = agreedOrders.size();

		// accumulate stake to see how many agreed chains are required to meet proposal
		// threshold
		double accumulatedStake = 0.0;
		int i = 0;
		for (; i < numAgreed; i++) {
			Order c = agreedOrders.get(i);
			double orderStake = stakedOrders.get(c);
			accumulatedStake += orderStake;
			if (accumulatedStake > THRESHOLD) break;
		}

		if (i < numAgreed) {
			// we have a proposed consensus
			Order lastAgreed = agreedOrders.get(i);
			AVector<SignedData<Block>> lastBlocks = lastAgreed.getBlocks();
			long newProposalPoint = winningBlocks.commonPrefixLength(lastBlocks);
			return winningOrder.withProposalPoint(newProposalPoint);
		} else {
			return winningOrder;
		}
	}

	/**
	 * Sorts a set of Orders according to level of agreement with a given vector of
	 * Blocks. Orders with longest common prefix length are placed first.
	 * 
	 * @param stakedOrders  Map with Orders as keys
	 * @param winningBlocks Vector of blocks to seek agreement with
	 * @return List of Orders in agreement order
	 */
	private ArrayList<Order> sortByAgreement(HashMap<Order, ?> stakedOrders, AVector<SignedData<Block>> winningBlocks) {
		return Utils.sortListBy(new Function<Order, Long>() {
			@Override
			public Long apply(Order c) {
				long match = winningBlocks.commonPrefixLength(c.getBlocks());
				return -match; // sort highest matches first
			}
		}, stakedOrders.keySet());
	}

	/**
	 * Gets an ordered list of new blocks from a collection of orderings. Ordering is a
	 * partial order based on when a block is first observed. This is an important
	 * heuristic (to avoid re-ordering new blocks from the same peer).
	 */
	private static ArrayList<SignedData<Block>> collectNewBlocks(Collection<AVector<SignedData<Block>>> orders, long consensusPoint) {
		// We want to preserve order, remove duplicates
		HashSet<SignedData<Block>> newBlocks = new HashSet<>();
		ArrayList<SignedData<Block>> newBlocksOrdered = new ArrayList<>();
		for (AVector<SignedData<Block>> blks : orders) {
			if (blks.count()<=consensusPoint) continue;
			Iterator<SignedData<Block>> it = blks.listIterator(consensusPoint);
			while (it.hasNext()) {
				SignedData<Block> b = it.next();
				if (!newBlocks.contains(b)) {
					newBlocks.add(b);
					newBlocksOrdered.add(b);
				}
			}
		}
		return newBlocksOrdered;
	}

	/**
	 * Compute the new winning Order for this Peer, including any new blocks
	 * encountered
	 * 
	 * @param stakedOrders Amount of stake on each distinct Order
	 * @param consensusPoint Current consensus point
	 * @param initialTotalStake Total stake under consideration
	 * @return Vector of Blocks in winning Order
	 */
	public static AVector<SignedData<Block>> computeWinningOrder(HashMap<Order, Double> stakedOrders, long consensusPoint,
			double initialTotalStake) {
		assert (!stakedOrders.isEmpty());
		// Get the Voting Set. Will be updated each round to winners of previous round.
		HashMap<AVector<SignedData<Block>>, Double> votingSet = combineToBlocks(stakedOrders);

		// Accumulate new blocks.
		ArrayList<SignedData<Block>> newBlocksOrdered = collectNewBlocks(votingSet.keySet(), consensusPoint);

		double totalStake = initialTotalStake;
		long point = consensusPoint;
		
		findWinner:
		while (votingSet.size() > 1) {
			// Accumulate candidate winning Blocks for this round, indexed by next Block
			HashMap<SignedData<Block>, HashMap<AVector<SignedData<Block>>, Double>> blockVotes = new HashMap<>();
			
			for (Map.Entry<AVector<SignedData<Block>>, Double> me : votingSet.entrySet()) {
				AVector<SignedData<Block>> blocks = me.getKey();
				long cCount = blocks.count();

				if (cCount <= point) continue; // skip Ordering with insufficient blocks: cannot win this round

				SignedData<Block> b = blocks.get(point);

				// update hashmap of Orders voting for each block (i.e. agreed on current Block)
				HashMap<AVector<SignedData<Block>>, Double> agreedOrders = blockVotes.get(b);
				if (agreedOrders == null) {
					agreedOrders = new HashMap<>();
					blockVotes.put(b, agreedOrders);
				}
				Double stake = me.getValue();
				agreedOrders.put(blocks, stake);
				if (stake > totalStake * 0.5) {
					// have a winner for sure, no point continuing so populate final Voting set and break
					votingSet.clear();
					votingSet.put(blocks, stake);
					break findWinner; 
				}
			}

			if (blockVotes.size() == 0) {
				// we have multiple chains, but no more blocks - so they should be all equal
				// we can break loop and continue with an arbitrary choice
				break findWinner;
			}

			Map.Entry<SignedData<Block>, HashMap<AVector<SignedData<Block>>, Double>> winningResult = null;
			double winningVote = Double.NEGATIVE_INFINITY;
			for (Map.Entry<SignedData<Block>, HashMap<AVector<SignedData<Block>>, Double>> me : blockVotes.entrySet()) {
				HashMap<AVector<SignedData<Block>>, Double> agreedChains = me.getValue();
				double blockVote = computeVote(agreedChains);
				if (blockVote > winningVote) {
					winningVote = blockVote;
					winningResult = me;
				} else if (blockVote==winningVote) {
					// tie break special case, choose lowest hash
					if (me.getKey().getHash().compareTo(winningResult.getKey().getHash())<0) {
						winningResult=me;
					}
				}
			}

			if (winningResult==null) throw new Error("This shouldn't happen!");
			votingSet = winningResult.getValue(); // Update Orderings to be included in next round
			totalStake = winningVote; // Total Stake among winning Orderings
			
			// advance to next block position for next round
			point++; 
		}
		
		if (votingSet.size() == 0) {
			// no vote for any Order. Might happen if the peer doesn't have any stake
			// and doesn't have any Orders from other peers with stake?
			return null;
		}
		AVector<SignedData<Block>> winningBlocks = votingSet.keySet().iterator().next();

		// add new blocks back to winning Order (if not already included)
		AVector<SignedData<Block>> fullWinningBlocks = appendNewBlocks(winningBlocks, newBlocksOrdered, consensusPoint);

		return fullWinningBlocks;
	}

	private static final AVector<SignedData<Block>> appendNewBlocks(AVector<SignedData<Block>> blocks, ArrayList<SignedData<Block>> newBlocksOrdered,
			long consensusPoint) {
		HashSet<SignedData<Block>> newBlocks = new HashSet<>();
		newBlocks.addAll(newBlocksOrdered);

		// exclude new blocks already in the base Order
		// TODO: what about blocks already in consensus?
		// Probably need to check last block time from Peer
		Iterator<SignedData<Block>> it = blocks.listIterator(Math.min(blocks.count(), consensusPoint));
		while (it.hasNext()) {
			newBlocks.remove(it.next());
		}
		newBlocksOrdered.removeIf(b -> !newBlocks.contains(b));

		// sort new blocks by timestamp and append to winning Order
		// must be a stable sort to maintain order from equal timestamps
		newBlocksOrdered.sort(Block.TIMESTAMP_COMPARATOR);

		AVector<SignedData<Block>> fullBlocks = blocks.appendAll(newBlocksOrdered);
		return fullBlocks;
	}

	/**
	 * Combine stakes from multiple orders to a single stake for each distinct Block ordering.
	 * 
	 * @param stakedOrders
	 * @return Map of AVector<Block> to total stake
	 */
	private static HashMap<AVector<SignedData<Block>>, Double> combineToBlocks(HashMap<Order, Double> stakedOrders) {
		HashMap<AVector<SignedData<Block>>, Double> result = new HashMap<>();
		for (Map.Entry<Order, Double> e : stakedOrders.entrySet()) {
			Order c = e.getKey();
			Double stake = e.getValue();
			AVector<SignedData<Block>> blocks = c.getBlocks();
			Double acc = result.get(blocks);
			if (acc == null) {
				result.put(blocks, stake);
			} else {
				result.put(blocks, acc + stake);
			}
		}
		return result;
	}

	/**
	 * Computes the total vote for all entries in a HashMap
	 * 
	 * @param <V> The type of values used as keys in the HashMap
	 * @param m   A map of values to votes
	 * @return The total voting stake
	 */
	public static <V> double computeVote(HashMap<V, Double> m) {
		double result = 0.0;
		for (Map.Entry<V, Double> me : m.entrySet()) {
			result += me.getValue();
		}
		return result;
	}

	/**
	 * Compute the total stake for every distinct Order seen. Stores results in
	 * a map of Orders to staked value.
	 * 
	 * @param peerOrders A map of peer addresses to signed proposed Orders
	 * @param peerStakes A map of peers addresses to weighted stakes for each peer
	 * @param dest       Destination hashmap to store the stakes for each Order
	 * @return The total stake of all chains among peers under consideration 
	 */
	public static double prepareStakedOrders(AMap<AccountKey, SignedData<Order>> peerOrders,
			HashMap<AccountKey, Double> peerStakes, HashMap<Order, Double> dest) {
		return peerOrders.reduceValues((acc, signedOrder) -> {
			try {
				// Get the Order for this peer
				Order order = signedOrder.getValue();
				AccountKey cAddress = signedOrder.getAccountKey();
				Double cStake = peerStakes.get(cAddress);
				if ((cStake == null) || (cStake == 0.0)) return acc;
				Double stake = dest.get(order);
				if (stake == null) {
					dest.put(order, cStake); // new Order to consider
				} else {
					dest.put(order, stake + cStake); // add stake to existing Order
				}
				return acc + cStake;
			} catch (Exception e) {
				throw Utils.sneakyThrow(e);
			}
		}, 0.0);
	}

	/**
	 * Gets the Order for the current peer specified by a MergeContext in this
	 * Belief
	 * 
	 * @param mc Merge context
	 * @return Order for current Peer, or null if not found
	 * @throws BadSignatureException 
	 */
	private Order getMyOrder(MergeContext mc) throws BadSignatureException {
		AccountKey myAddress = mc.getAccountKey();
		SignedData<Order> signed = (SignedData<Order>) orders.get(myAddress);
		if (signed == null) return null;
		assert (signed.getAccountKey().equals(myAddress));
		return signed.getValue();
	}

	/**
	 * Updates this Belief with a new set of Chains for each peer address
	 * 
	 * @param newOrders New map of peer keys to Orders
	 * @return The updated belief, or the same Belief if no change.
	 */
	public Belief withOrders(BlobMap<AccountKey, SignedData<Order>> newOrders) {
		if (newOrders == orders) return this;
		return Belief.create(newOrders,timestamp);
	}
	

	public Belief withTimestamp(long newTimestamp) {
		if (timestamp==newTimestamp) return this;
		return Belief.create(orders,newTimestamp);
	}


	@Override
	public int encode(byte[] bs, int pos) {
		bs[pos++]=getTag();
		return encodeRaw(bs,pos);
	}
	
	@Override
	public int estimatedEncodingSize() {
		return 1+orders.estimatedEncodingSize()+12;
	}

	/**
	 * Read a Belief from a ByteBuffer. Assumes tag already read.
	 * @param bb ByteBuffer to read from
	 * @return Belief instance
	 * @throws BadFormatException If encoding is invalid
	 */
	public static Belief read(ByteBuffer bb) throws BadFormatException {
		BlobMap<AccountKey, SignedData<Order>> orders = Format.read(bb);
		if (orders == null) throw new BadFormatException("Null orders in Belief");
		CVMLong timestamp = Format.read(bb);
		if (timestamp == null) throw new BadFormatException("Null timestamp");
		return new Belief(orders, timestamp.longValue());
	}
	
	public static Belief read(Blob b, int pos) throws BadFormatException {
		int epos=pos+1; // skip tag
		
		BlobMap<AccountKey, SignedData<Order>> orders = Format.read(b,epos);
		if (orders == null) throw new BadFormatException("Null orders in Belief");
		epos+=Format.getEncodingLength(orders);
		
		CVMLong timestamp = Format.read(b,epos);
		if (timestamp == null) throw new BadFormatException("Null timestamp");
		epos+=Format.getEncodingLength(timestamp);

		Belief result= new Belief(orders, timestamp.longValue());
		result.attachEncoding(b.slice(pos, epos));
		return result;
	}


	@Override
	public byte getTag() {
		return Tag.BELIEF;
	}

	/**
	 * Gets the current Order for a given Address within this Belief.
	 * 
	 * @param address Address of peer
	 * @return The chain for the peer within this Belief, or null if noy found.
	 */
	public Order getOrder(AccountKey address) {
		SignedData<Order> sc = orders.get(address);
		if (sc == null) return null;
		return sc.getValue();
	}

	/**
	 * Get the map of orders for this Belief
	 * @return Orders map
	 */
	public BlobMap<AccountKey, SignedData<Order>> getOrders() {
		return orders;
	}

	@Override
	public void validateCell() throws InvalidDataException {
		if (orders == null) throw new InvalidDataException("Null orders", this);
		orders.validateCell();
	}

	/**
	 * Returns the timestamp of this Belief. A Belief should have a new timestamp if
	 * and only if the Peer incorporates new information.
	 * @return Timestamp of belief
	 */
	public long getTimestamp() {
		return timestamp;
	}
	
	@Override 
	public boolean equals(ACell a) {
		if (!(a instanceof Belief)) return false;
		Belief as=(Belief)a;
		return equals(as);
	}
	
	/**
	 * Tests if this Belief is equal to another
	 * @param a Belief to compare with
	 * @return true if equal, false otherwise
	 */
	public boolean equals(Belief a) {
		if (this == a) return true; // important optimisation for e.g. hashmap equality
		if (a == null) return false;
		Hash h=this.cachedHash();
		if (h!=null) {
			Hash ha=a.cachedHash();
			if (ha!=null) return Utils.equals(h, ha);
		}

		if (timestamp!=a.timestamp) return false;
		if (!orders.equals(a.orders)) return false;
		return true;
	}

	@Override
	public int getRefCount() {
		return orders.getRefCount();
	}
	
	@Override 
	public <R extends ACell> Ref<R> getRef(int i) {
		return orders.getRef(i);
	}

	/**
	 * Gets a new HashMap containing all Orders
	 * @return HashMap of current orders
	 */
	public HashMap<AccountKey, SignedData<Order>> getOrdersHashMap() {
		int n=orders.size();
		HashMap<AccountKey, SignedData<Order>> hm=new HashMap<>(n);
		for (int i=0; i<n; i++) {
			MapEntry<AccountKey, SignedData<Order>> entry=orders.entryAt(i);
			AccountKey key=RT.ensureAccountKey(entry.getKey());
			hm.put(key, entry.getValue());
		}
		return hm;
	}

	@Override
	public RecordFormat getFormat() {
		return BELIEF_FORMAT;
	}





}
