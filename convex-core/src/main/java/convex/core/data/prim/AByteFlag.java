package convex.core.data.prim;

import convex.core.data.ACell;
import convex.core.data.IRefFunction;
import convex.core.data.Ref;
import convex.core.exceptions.InvalidDataException;

public abstract class AByteFlag extends APrimitive {
	
	public static final int MAX_ENCODING_LENGTH = 1;

	@Override
	public int getRefCount() {
		// Never any refs
		return 0;
	}

	@Override
	public int estimatedEncodingSize() {
		return 1;
	}

	@Override
	public void validateCell() throws InvalidDataException {
		// Nothing to check. Always valid
	}
	
	@Override
	public ACell updateRefs(IRefFunction func) {
		return this;
	}
	
	@Override
	public <R extends ACell> Ref<R> getRef(int i) {
		throw new IndexOutOfBoundsException(i);
	}
	
	@Override
	public int encode(byte[] bs, int pos) {
		bs[pos++]=getTag();
		return pos;
	}

	@Override
	public int encodeRaw(byte[] bs, int pos) {
		// no raw data to encode, everything is in tag
		return pos;
	}
}
