package convex.core.cvm;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import convex.core.Constants;
import convex.core.ErrorCodes;
import convex.core.Result;
import convex.core.ResultContext;
import convex.core.cpos.Belief;
import convex.core.cpos.BeliefMerge;
import convex.core.cpos.Block;
import convex.core.cpos.BlockResult;
import convex.core.cpos.CPoSConstants;
import convex.core.cpos.Order;
import convex.core.crypto.AKeyPair;
import convex.core.cvm.transactions.ATransaction;
import convex.core.data.ACell;
import convex.core.data.AMap;
import convex.core.data.AVector;
import convex.core.data.AccountKey;
import convex.core.data.Hash;
import convex.core.data.Keyword;
import convex.core.data.Maps;
import convex.core.data.Ref;
import convex.core.data.SignedData;
import convex.core.data.Strings;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMLong;
import convex.core.exceptions.InvalidDataException;
import convex.core.init.Init;
import convex.core.lang.RT;
import convex.core.store.AStore;
import convex.core.store.Stores;
import convex.core.util.Utils;

/**
 * <p>
 * Immutable class representing the encapsulated state of a Peer
 * </p>
 *
 * SECURITY:
 * <ul>
 * <li>Needs to contain the Peer's unlocked private key for online signing.</li>
 * <li>Manages Peer state transitions given external events. Must do so
 * correctly.</li>
 * </ul>
 *
 * <p>
 * Must have at least one state, the initial state. New states will be added as
 * consensus updates happen.
 * </p>
 *
 *
 * "Don't worry about what anybody else is going to do. The best way to predict
 * the future is to invent it." - Alan Kay
 */
public class Peer {
	/** This Peer's key */
	private final AccountKey peerKey;

	/** 
	 * This Peer's key pair 
	 * 
	 *  Make transient to mark that this should never be Persisted by accident
	 */
	private transient final AKeyPair keyPair;
	
	/** The latest merged belief */
	private final Belief belief;

	/**
	 * The latest observed timestamp. This is increased by the Server polling the
	 * local clock.
	 */
	private final long timestamp;
	
	/**
	 * Current state index
	 */
	private final long statePosition;
	
	/**
	 * Current consensus Order
	 */
	private final Order consensusOrder;

	
	/**
	 * Current base history position, from which results are stored
	 */
	private final long historyPosition;


	/**
	 * Current consensus state 
	 */
	private final State state;
	
	/**
	 * Current consensus state 
	 */
	private final State genesis;

	/**
	 * Vector of results
	 */
	private final AVector<BlockResult> blockResults;

	private Peer(AKeyPair kp, Belief belief, Order consensusOrder, long statePos, State state, State genesis, long history, AVector<BlockResult> results,
			long timeStamp) {
		this.keyPair = kp;
		this.peerKey = kp.getAccountKey();
		this.belief = belief;
		this.state = state;
		this.genesis=genesis;
		this.timestamp = timeStamp;
		
		this.consensusOrder=consensusOrder;
		this.statePosition=statePos;
		
		this.historyPosition=history;
		this.blockResults = results;
	}

	/**
	 * Constructs a Peer instance from persisted Peer Data
	 * @param keyPair Key Pair for Peer
	 * @param peerData Peer data map
	 * @return New Peer instance
	 */
	@SuppressWarnings("unchecked")
	public static Peer fromData(AKeyPair keyPair,AMap<Keyword, ACell> peerData)  {
		Belief belief=(Belief) peerData.get(Keywords.BELIEF);
		AVector<BlockResult> results=(AVector<BlockResult>) peerData.get(Keywords.RESULTS);
		State genesis=(State) peerData.get(Keywords.GENESIS);
		State state=(State) peerData.get(Keywords.STATE);
		long pos=((CVMLong) peerData.get(Keywords.POSITION)).longValue();
		Order co=((Order) peerData.get(Keywords.ORDER));
		long hpos=((CVMLong) peerData.get(Keywords.HISTORY)).longValue();
		long timestamp=((CVMLong) peerData.get(Keywords.TIMESTAMP)).longValue();
		// This gets inferred from keypair, caller might want to check it is correct though!
		// AccountKey key=AccountKey.parse(peerData.get(Keywords.KEY));
		
		
		return new Peer(keyPair,belief,co,pos,state,genesis,hpos,results,timestamp);
	}

	/**
	 * Gets the Peer Data map for this Peer
	 * @return Peer data
	 */
	public AMap<Keyword, ACell> toData() {
		return Maps.of(
			Keywords.BELIEF,belief,
			Keywords.HISTORY,CVMLong.create(historyPosition),
			Keywords.ORDER,consensusOrder,
			Keywords.RESULTS,blockResults,
			Keywords.POSITION,CVMLong.create(statePosition),
			Keywords.STATE,state,
			Keywords.KEY,peerKey,
			Keywords.GENESIS,genesis,
			Keywords.TIMESTAMP,timestamp
		);
	}

	/**
	 * Creates a Peer
	 * @param peerKP Key Pair
	 * @param genesis Genesis State
	 * @return New Peer instance
	 */
	public static Peer create(AKeyPair peerKP, State genesis) {
		Belief belief = Belief.createSingleOrder(peerKP);
		
		
		return new Peer(peerKP, belief, Order.create(),0L,genesis,genesis, 0,Vectors.empty(),genesis.getTimestamp().longValue());
	}
	
	/**
	 * Create a Peer instance from a remotely acquired Belief
	 * @param peerKP Peer KeyPair
	 * @param genesisState Initial genesis State of the Network
	 * @param initialBelief Initial belief
	 * @return New Peer instance
	 * @throws InvalidDataException if invalid data was found in merged belief
	 */
	public static Peer create(AKeyPair peerKP, State genesisState, Belief initialBelief) throws InvalidDataException {
		Peer peer=create(peerKP,genesisState);
		peer=peer.updateTimestamp(Utils.getCurrentTimestamp());
		peer=peer.updateBelief(initialBelief);
		return peer;
	}
	
	/**
	 * Create a Peer instance from a remotely acquired State and Order
	 * @param peerKP Peer KeyPair
	 * @param genesisState Initial genesis State of the Network
	 * @param remoteBelief Remote belief to sync with
	 * @return New Peer instance
	 */
//	public static Peer create(AKeyPair peerKP, State genesisState, State consensusState, SignedData<Order> order) {
//		SignedData<Order> myOrder=peerKP.signData(order.getValue());
//		Belief b=Belief.create(order,myOrder); // two orders in Belief at least....
//		Peer peer=create(peerKP,genesisState,consensusState,b);
//		peer=peer.updateTimestamp(Utils.getCurrentTimestamp());
//		peer=peer.mergeBeliefs(remoteBelief);
//		return peer;
//	}

	/**
	 * Like {@link #restorePeer(AStore, AKeyPair, ACell)} but uses a null root key.
	 * @param store Store to restore from
	 * @param keyPair Key Pair to use for restored Peer
	 * @return Restored Peer instance
	 * @throws IOException In case of IO error
	 */
	public static Peer restorePeer(AStore store, AKeyPair keyPair) throws IOException {
		return restorePeer(store, keyPair, null);
	}

	/**
	 * Restores a Peer from the Etch database specified in Config
	 * @param store Store to restore from
	 * @param keyPair Key Pair to use for restored Peer
	 * @param rootKey When not null, assumes the root data is a map and peer data is under that key
	 * @return Peer instance, or null if root hash was not found
	 * @throws IOException If store reading failed
	 */
	public static Peer restorePeer(AStore store, AKeyPair keyPair, ACell rootKey) throws IOException {
		AMap<Keyword,ACell> peerData=getPeerData(store, rootKey);
		if (peerData==null) return null;
		Peer peer=Peer.fromData(keyPair,peerData);
		return peer;
	}

	/**
	 * Gets Peer Data from a Store.
	 * 
	 * @param store Store to retrieve Peer Data from
	 * @param rootKey When not null, assumes the root data is a map and peer data is under that key
	 * @return Peer data map, or null if not available
	 * @throws IOException If a store IO error occurs
	 */
	@SuppressWarnings("unchecked")
	public static AMap<Keyword, ACell> getPeerData(AStore store, ACell rootKey) throws IOException {
		Stores.setCurrent(store);
		Hash root = store.getRootHash();
		Ref<ACell> ref=store.refForHash(root);
		if (ref==null) return null; // not found case
		if (ref.getStatus()<Ref.PERSISTED) return null; // not fully in store
		
		if (rootKey == null) return (AMap<Keyword,ACell>)ref.getValue();
		else return (AMap<Keyword,ACell>)((AMap<ACell,ACell>)ref.getValue()).get(rootKey);
	}

	/**
	 * Creates a new Peer instance at server startup using the provided
	 * configuration. Current store must be set to store for server.
	 * 
	 * @param keyPair Key pair for genesis peer
	 * @param genesisState Genesis state, or null to generate fresh state
	 * @return A new Peer instance
	 */
	public static Peer createGenesisPeer(AKeyPair keyPair, State genesisState) {
		if (keyPair == null) throw new IllegalArgumentException("Peer initialisation requires a keypair");

		if (genesisState == null) {
			genesisState=Init.createState(Utils.listOf(keyPair.getAccountKey()));
			genesisState=genesisState.withTimestamp(Utils.getCurrentTimestamp());
		}

		return create(keyPair, genesisState);
	}


	/**
	 * Updates the timestamp to the specified time, going forwards only
	 *
	 * @param newTimestamp New Peer timestamp
	 * @return This peer updated with the given timestamp
	 */
	public Peer updateTimestamp(long newTimestamp) {
		if (newTimestamp <= timestamp) return this;
		return new Peer(keyPair, belief, consensusOrder,statePosition,state,genesis, historyPosition,blockResults, newTimestamp);
	}

	/**
	 * Compiles and executes a query on the current consensus state of this Peer.
	 *
	 * @param form Form to compile and execute.
	 * @param address Address to use for query execution. If null, core address will be used
	 * @return The Context containing the query results. Will be NOBODY error if address / account does not exist
	 */
	public ResultContext executeQuery(ACell form, Address address) {
		State state=getConsensusState();

		if (form instanceof ATransaction) {
			return executeDetached((ATransaction)form);
		}
		
		if (form instanceof SignedData) {
			SignedData<?> sc=(SignedData<?>)form;
			ACell val=sc.getValue();
			if (form instanceof ATransaction) {
				return executeDetached((ATransaction)val);
			}
		}
		
		if (address==null) {
			address=Init.getGenesisAddress();
			//return  Context.createFake(state).withError(ErrorCodes.NOBODY,"Null Address provided for query");
		} else if (!state.hasAccount(address)) {
			return  ResultContext.error(state,ErrorCodes.NOBODY,"Query for non-existant account");
		}

		// Run query in a fake context
		Context ctx=Context.create(state, address, Constants.MAX_TRANSACTION_JUICE);
		ctx=ctx.run(form);
		ResultContext rctx=ResultContext.fromContext(ctx);
		return rctx;
	}

	/**
	 * Executes a "detached" transaction on the current consensus state of this Peer, but without any effect on current CVM state.
	 * This can be used for query, estimating potential fees etc.
	 *
	 * @param transaction Transaction to execute
	 * @return The Context containing the transaction results.
	 */
	public ResultContext executeDetached(ATransaction transaction) {
		State s=getConsensusState();
		ResultContext ctx=getConsensusState().applyTransaction(transaction,TransactionContext.create(s));
		return ctx;
	}

	/**
	 * Executes a query in this Peer's current Consensus State, using a default address
	 * @param form Form to execute as a Query
	 * @return Context after executing query
	 */
	public ResultContext executeQuery(ACell form) {
		return executeQuery(form,Init.getGenesisAddress());
	}

	/**
	 * Gets the timestamp of this Peer
	 * @return Timestamp
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * Gets the Peer Public Key of this Peer.
	 * @return Peer Key of Peer.
	 */
 	public AccountKey getPeerKey() {
		return peerKey;
	}
 	
	/**
	 * Gets the controller Address for this Peer
	 * @return Address of Peer controller Account, or null if does not exist
	 */
 	public Address getController() {
		PeerStatus ps= getConsensusState().getPeer(peerKey);
		if (ps==null) return null;
		return ps.getController();
	}
 	
	/**
	 * Gets the Peer Key of this Peer.
	 * @return Address of Peer.
	 */
 	public AKeyPair getKeyPair() {
		return keyPair;
	}

 	/**
 	 * Get the current Belief of this Peer
 	 * @return Belief
 	 */
	public Belief getBelief() {
		return belief;
	}

	/**
	 * Signs a value with the keypair of this Peer
	 * @param <T> Type of value to sign
	 * @param value Value to sign
	 * @return Signed data value
	 */
	public <T extends ACell> SignedData<T> sign(T value) {
		return keyPair.signData(value);
	}

	/**
	 * Gets the current consensus state for this Peer
	 *
	 * @return Consensus state for this chain (genesis state if no block consensus)
	 */
	public State getConsensusState() {
		return state;
	}

	/**
	 * Merges a set of new Beliefs into this Peer's belief. Beliefs may be null, in
	 * which case they are ignored. Should call updateState() at some point after this to
	 * update the global consensus state
	 *
	 * @param beliefs An array of Beliefs. May contain nulls, which will be ignored.
	 * @return Updated Peer after Belief Merge
	 * @throws InvalidDataException if 
	 *
	 */
	public Peer mergeBeliefs(Belief... beliefs) throws InvalidDataException {
		Belief belief=getBelief();
		BeliefMerge mc = BeliefMerge.create(belief,keyPair, timestamp, getConsensusState());
		Belief newBelief = mc.merge(beliefs);

		long ocp=getFinalityPoint();
		Order newOrder=newBelief.getOrder(peerKey);
		long ncp=newOrder.getConsensusPoint(CPoSConstants.CONSENSUS_LEVEL_FINALITY);
		if (ocp>ncp) {
			// TODO: check cases where this can occur
			// System.err.println("Receding consensus? Old CP="+ocp +", New CP="+ncp);	
		}
		Peer p= updateBelief(newBelief);
		if (p==this) return this;
		
		return p;
	}
	
	/**
	 * Prunes History before the given timestamp
	 * @param ts Timestamp from which to to keep History
	 * @return Updated Peer with pruned History
	 */
	public Peer pruneHistory(long ts) {
		// Return this if we don't possibly have anything to prune
		if (blockResults.count()==0) return this;
		
		// Exit without change if there is nothing before the given timestamp to prune
		long firstTs=blockResults.get(0).getState().getTimestamp().longValue();
		if (ts<firstTs) return this;
		
		@SuppressWarnings("unused")
		long ix=Utils.binarySearch(blockResults, br->br.getState().getTimestamp(), (a,b)->a.compareTo(b), CVMLong.create(ts));
		// TODO: complete pruning
		return this;
	}

	/**
	 * Update this Peer with a new consensus Belief
	 *
	 * @param newBelief Belief to apply
	 * @return Updated Peer
	 */
	public Peer updateBelief(Belief newBelief) {
		if (belief == newBelief) return this;
		Order order=belief.getOrder(peerKey);
		if (order==null) order=this.consensusOrder;
		// System.out.println(Lists.of(order.getConsensusPoints()));
		return new Peer(keyPair, newBelief, order,statePosition,state, genesis, historyPosition,blockResults, timestamp);
	}	
	
	/**
	 * Updates the state of the Peer based on latest consensus Belief
	 * @return Updated Peer
	 */
	public Peer updateState() {
		Order myOrder = belief.getOrder(peerKey); // this peer's Order from latest belief
		long consensusPoint = myOrder.getConsensusPoint(CPoSConstants.CONSENSUS_LEVEL_FINALITY);
		AVector<SignedData<Block>> blocks = myOrder.getBlocks();
		AVector<SignedData<Block>> consensusBlocks= consensusOrder.getBlocks();

		State s = this.state;
		AVector<BlockResult> newResults = this.blockResults;
		long stateIndex=statePosition;
		long consensusMatch=blocks.commonPrefixLength(consensusBlocks);
		if (consensusMatch<stateIndex) {
			if (!CPoSConstants.ENABLE_FORK_RECOVERY) {
				throw new IllegalStateException("Network Fork detected but fork recovery diabled!");
			}
			
			// We need to rollback to recover from a fork!!
			stateIndex=consensusMatch;
			s=getHistoricalState(consensusMatch);
			if (s==null) throw new IllegalStateException("Can't get historical state for position: "+consensusMatch);
			
			// Discard history
			long historyStart=consensusMatch-historyPosition;
			newResults=newResults.slice(0, historyStart);
		}
		
		// Return if we don't need to advance states
		if (stateIndex>=consensusPoint) return this;
		
		// Kick off parallel signature validation
		validateSignatures(s,blocks,stateIndex,consensusPoint);

		// We need to compute at least one new state update
		while (stateIndex < consensusPoint) { // add states until last state is at consensus point
			SignedData<Block> block = blocks.get(stateIndex);
			
			BlockResult br = s.applyBlock(block);
			State newState=br.getState();
			newResults = newResults.append(br);
			
			if (newState.equals(s)) {
				// Nothing changes in CVM state, so the Block must have been invalid!
				if (peerKey.equals(block.getAccountKey())) {
					// We messed up, or someone was messing with us in a serious way.....
				}
			} 
			s=newState;
			stateIndex++;
		}
		return new Peer(keyPair, belief, myOrder,stateIndex,s, genesis, historyPosition,newResults, timestamp);
	}

	private void validateSignatures(State s, AVector<SignedData<Block>> blocks, long start, long end) {
		Consumer<SignedData<ATransaction>> transactionValidator=st->{
			ATransaction t=st.getValue();
			Address origin=t.getOrigin();
			AccountStatus as=s.getAccount(origin);
			if (as==null) return; // ignore, will probably fail with :NOBODY
			AccountKey pk=as.getAccountKey();
			if (pk==null) return; // ignore, will probably fail as an actor account
			st.checkSignature(pk);
		};
		
		Consumer<SignedData<Block>> blockValidator=sb->{
			AVector<SignedData<ATransaction>> transactions = sb.getValue().getTransactions();
			Stream<SignedData<ATransaction>> tstream=transactions.parallelStream();
			tstream.forEach(transactionValidator);
		};
		
		Stream<SignedData<Block>> stream=blocks.parallelStream().skip(start).limit(end-start);
		stream.forEach(blockValidator);
	}

	/**
	 * Gets a historical State for the specified position
	 * @param consensusMatch
	 * @return Historical state, or null if not available
	 */
	private State getHistoricalState(long pos) {
		if (pos==0) return genesis;
		long hpos=pos-historyPosition;
		if (hpos<1) return null;
		if (hpos>blockResults.count()) return null;
		
		BlockResult br=blockResults.get(hpos-1);
		return br.getState();
	}

	/**
	 * Gets the Result of a specific transaction
	 * @param blockIndex Index of Block in Order
	 * @param txIndex Index of transaction in block
	 * @return Result from transaction, or null if the transaction does not exist or is no longer stored
	 */
	public Result getResult(long blockIndex, long txIndex) {
		BlockResult br=getBlockResult(blockIndex);
		if (br==null) return null;
		return br.getResult(txIndex);
	}

	/**
	 * Gets the BlockResult of a specific block index
	 * @param i Index of Block
	 * @return BlockResult, or null if the BlockResult is not stired
	 */
	public BlockResult getBlockResult(long i) {
		if (i<historyPosition) return null; // Ancient history
		long brix=i-historyPosition;
		if (brix>=blockResults.count()) return null;
		return blockResults.get(brix);
	}

	/**
	 * Propose a new Block. Adds the Block to the current proposed Order for this
	 * Peer. Also increments Peer timestamp if necessary for new Block
	 *
	 * @param block Block to publish
	 * @return Peer after proposing new Block in Peer's own Order
	 */
	public Peer proposeBlock(Block block) {
		
		SignedData<Block> signedBlock=sign(block);
		@SuppressWarnings("unchecked")
		Belief newBelief=belief.proposeBlock(keyPair, signedBlock);
		
		Peer result=this;
		// Update timestamp if necessary to accommodate Block
		long blockTimeStamp=block.getTimeStamp();
		if (blockTimeStamp>result.getTimestamp()) {
			result=result.updateTimestamp(blockTimeStamp);
		}
		
		result=result.updateBelief(newBelief);
		// result=result.updateState();
		return result;
	}
	
	public Result checkTransaction(SignedData<ATransaction> sd) {

		// TODO: throttle?
		ATransaction tx=RT.ensureTransaction(sd.getValue());
		
		// System.out.println("transact: "+v);
		if (tx==null) {
			return Result.error(ErrorCodes.FORMAT,Strings.BAD_FORMAT);
		}
		
		State s=getConsensusState();
		AccountStatus as=s.getAccount(tx.getOrigin());
		if (as==null) {
			return Result.error(ErrorCodes.NOBODY, Strings.NO_SUCH_ACCOUNT);
		}
		
		if (tx.getSequence()<=as.getSequence()) {
			return Result.error(ErrorCodes.SEQUENCE, Strings.OLD_SEQUENCE);
		}
		
		AccountKey expectedKey=as.getAccountKey();
		if (expectedKey==null) {
			return Result.error(ErrorCodes.STATE, Strings.NO_TX_FOR_ACTOR);
		}
		
		AccountKey pubKey=sd.getAccountKey();
		if (!expectedKey.equals(pubKey)) {
			return Result.error(ErrorCodes.SIGNATURE, Strings.WRONG_KEY );
		}
		
		if (!sd.checkSignature()) {
			// SECURITY: Client tried to send a badly signed transaction!
			return Result.error(ErrorCodes.SIGNATURE, Strings.BAD_SIGNATURE);
		}

		// All checks passed OK!
		return null;
	}

	/**
	 * Gets the Final Point for this Peer
	 * @return Consensus Point value
	 */
	public long getFinalityPoint() {
		Order order=getPeerOrder();
		if (order==null) return 0;
		return order.getConsensusPoint(CPoSConstants.CONSENSUS_LEVEL_FINALITY);
	}

	/**
	 * Gets the current Order for this Peer
	 *
	 * @return The Order for this peer in its current Belief. Will return null if the Peer is not a peer in the current consensus state
	 *
	 */
	public Order getPeerOrder() {
		return getBelief().getOrder(peerKey);
	}

	/**
	 * Gets the current chain this Peer sees for a given peer address
	 *
	 * @param peerKey Peer Key
	 * @return The current Order for the specified peer
	 */
	public Order getOrder(AccountKey peerKey) {
		return getBelief().getOrder(peerKey);
	}


	/**
	 * Get the Network ID for this PEer
	 * @return Network ID
	 */
	public Hash getNetworkID() {
		return genesis.getHash();
	}

	/**
	 * Gets the state position of this Peer, which is equal to the number of state transitions executed.
	 * @return Position
	 */
	public long getStatePosition() {
		return statePosition;
	}

	/**
	 * Gets the genesis State of this Peer
	 * @return Genesis State
	 */
	public State getGenesisState() {
		return genesis;
	}

	/**
	 * Checks if the Peer is ready to publish a Block. Requires sufficient stake
	 * @return true if ready to publish, false otherwise
	 */
	public boolean isReadyToPublish() {
		// If we are the only peer, always allow publishing
		if (state.getPeers().count()<=1) return true;
		
		PeerStatus ps=state.getPeer(peerKey);
		if (ps==null) return false;
		if (ps.getBalance()<CPoSConstants.MINIMUM_EFFECTIVE_STAKE) return false; 
		return true;
	}

	/**
	 * Gets the genesis state hash for this peer
	 * @return
	 */
	public Hash getGenesisHash() {
		return getGenesisState().getHash();
	}
}
