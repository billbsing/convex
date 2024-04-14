package convex.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import convex.core.State;
import convex.core.data.ACell;
import convex.core.data.AString;
import convex.core.data.AVector;
import convex.core.data.Cells;
import convex.core.data.Hash;
import convex.core.data.Ref;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMLong;
import convex.core.exceptions.InvalidDataException;
import convex.core.init.InitTest;
import convex.core.store.AStore;
import convex.core.store.MemoryStore;
import convex.core.store.Stores;
import convex.test.Samples;
import etch.EtchStore;

public class StoresTest {
	EtchStore testStore=EtchStore.createTemp();

	@Test public void testInitState() throws InvalidDataException {
		AStore temp=Stores.current();
		try {
			Stores.setCurrent(testStore);

			// Use fresh State
			State s=InitTest.createState();
			Ref<State> sr=Cells.persist(s).getRef();

			Hash hash=sr.getHash();

			Ref<State> sr2=Ref.forHash(hash);
			State s2=sr2.getValue();
			s2.validate();
		} finally {
			Stores.setCurrent(temp);
		}
	}
	
	@Test public void testCrossStores() throws InvalidDataException {
		AStore m1=new MemoryStore();
		AStore m2=new MemoryStore();
		
		AStore e1=testStore;
		AStore e2=EtchStore.createTemp();
		
		// non-emebdded single Cell
		AString nv=Samples.NON_EMBEDDED_STRING; 
		assertFalse(nv.isEmbedded());
		assertTrue(nv.isCompletelyEncoded());
		
		// small fully embedded cell
		CVMLong ev=CVMLong.ONE;
		assertTrue(ev.isEmbedded());
		assertTrue(ev.isCompletelyEncoded());
		
		AVector<?> v=Vectors.of(Vectors.of(nv,ev),nv,ev);
		
		Consumer<ACell> crossTest=x->{
			doCrossStoreTest(x,e1,e2);
			doCrossStoreTest(x,m1,e1);
			doCrossStoreTest(x,e2,m2);			
			doCrossStoreTest(x,m1,m2);
		};
		
		AStore temp=Stores.current();
		try {
			Stores.setCurrent(e1);
			assertSame(ev,Cells.persist(ev));
			
			// vector shouldn't be in other stores
			Hash hv=v.getHash();
			assertNull(e2.refForHash(hv));
			assertNull(m1.refForHash(hv));
			
			crossTest.accept(v);
			crossTest.accept(nv);
			crossTest.accept(ev);
			crossTest.accept(null);
		} finally {
			Stores.setCurrent(temp);
		}
	}

	private void doCrossStoreTest(ACell a, AStore s1, AStore s2) {
		Hash ha=Cells.getHash(a);
		
		ACell a1=Cells.persist(a, s1);
		assertSame(a1,Cells.persist(a1,s1));
		
		ACell a2=Cells.persist(a1, s2);
		assertSame(a2,Cells.persist(a2,s2));
		
		assertNotNull(s2.refForHash(ha));
		assertEquals(a1,a2);
	}
}
