package convex.core.data;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import convex.core.crypto.Hashing;
import convex.core.data.prim.CVMLong;
import convex.core.data.type.AType;
import convex.core.data.type.Types;
import convex.core.data.util.BlobBuilder;
import convex.core.exceptions.InvalidDataException;
import convex.core.util.Bits;

/**
 * Abstract base class for data objects containing immutable chunks of binary
 * data. Representation is equivalent to a fixed size immutable byte sequence.
 * 
 * Rationale: - Allow data to be encapsulated as an immutable object - Provide
 * specialised methods for processing byte data - Provide a cached Hash value,
 * lazily computed on demand
 * 
 */
public abstract class ABlob extends ABlobLike<CVMLong>  {

	@Override
	public AType getType() {
		return Types.BLOB;
	}
	
	/**
	 * Gets the length of this Blob
	 * 
	 * @return The length in bytes of this data object
	 */
	@Override
	public abstract long count();
	
	@Override
	public CVMLong get(long ix) {
		return CVMLong.forByte(byteAt(ix));
	}
	
	@Override
	public Ref<CVMLong> getElementRef(long index) {
		return get(index).getRef();
	}
	
	@Override
	public Blob empty() {
		return Blob.EMPTY;
	}

	/**
	 * Converts this blob to a readable byte buffer. 
	 * 
	 * WARNING: may be large. May refer to underlying byte array so should not be mutated
	 * 
	 * @return ByteBuffer with position zero (ready to read)
	 */
	public ByteBuffer toByteBuffer() {
		return ByteBuffer.wrap(getBytes());
	}

	/**
	 * Gets a contiguous slice of this Blob, as a new Blob.
	 * 
	 * Shares underlying backing data where possible
	 * 
	 * @param start  Start position for the created slice (inclusive)
	 * @param end End of the slice (exclusive)
	 * @return A blob of the specified length, representing a slice of this blob, or null if the slice is invalid
	 */
	public abstract ABlob slice(long start, long end);

	/**
	 * Gets a slice of this blob, as a new blob, starting from the given offset and
	 * extending to the end of the blob.
	 * 
	 * Shares underlying backing data where possible. Returned Blob may not be the
	 * same type as the original Blob
	 * @param start Start position to slice from
	 * @return Slice of Blob
	 */
	public ABlob slice(long start) {
		return slice(start, count());
	}

	@Override
	public abstract Blob toFlatBlob();


	/**
	 * Computes the hash of the byte data stored in this Blob, using the default MessageDigest.
	 * 
	 * This is the correct hash ID for a data value if this blob contains the data value's encoding
	 * 
	 * @return The Hash
	 */
	public Hash getContentHash() {
		// Note: We override and cache in Blob to ensure encoding hashes are cached
		return computeHash(Hashing.getDigest());
	}
	
	/**
	 * Computes the hash of the byte data stored in this Blob, using the given MessageDigest.
	 * 
	 * @param digest MessageDigest instance
	 * @return The hash
	 */
	public final Hash computeHash(MessageDigest digest) {
		updateDigest(digest);
		return Hash.wrap(digest.digest());
	}

	protected abstract void updateDigest(MessageDigest digest);

	/**
	 * Gets the byte at the specified position 
	 * 
	 * @param i Index of the byte to get
	 * @return The byte at the specified position
	 */
	@Override
	public byte byteAt(long i) {
		if ((i < 0) || (i >= count())) {
			throw new IndexOutOfBoundsException("Index: " + i);
		}
		return byteAtUnchecked(i);
	}
	


	/**
	 * Append an additional Blob to this, creating a new Blob as needed. New Blob will be canonical.
	 * 
	 * @param d Blob to append
	 * @return A new Blob, containing the additional data appended to this blob.
	 */
	public abstract ABlob append(ABlob d);

	/**
	 * Determines if this Blob is equal to another Object.
	 * 
	 * Blobs are defined to be equal if they have the same on-chain representation,
	 * i.e. if and only if all of the following are true:
	 * 
	 * - Blob is of the same general type 
	 * - Blobs are of the same length 
	 * - All byte values are equal
	 */
	@Override
	public boolean equals(ACell o) {
		if (o==this) return true; // fast path, avoid a type check / cast
		// only a Blob can be equal to a Blob
		if (!(o instanceof ABlob)) return false;
		return equals((ABlob)o);
	}
	
	/**
	 * Determines if this Blob is equal to another Blob.
	 * 
	 * Blobs are defined to be equal if they have the same on-chain representation,
	 * i.e. if and only if all of the following are true:
	 * 
	 * - Blob is of the same general type 
	 * - Blobs are of the same length 
	 * - All byte values are equal
	 * 
	 * @param o Blob to compare with
	 * @return true if Blobs are equal, false otherwise
	 */
	public abstract boolean equals(ABlob o);
	
	@Override
	public abstract ABlob toCanonical();

	/**
	 * Tests if the byte contents of this instance are equal to a subset of a byte array
	 * @param bytes Byte array to compare with
	 * @param offset Offset into byte array from which to start comparison
	 * @return true if exactly equal, false otherwise
	 */
	public abstract boolean equalsBytes(byte[] bytes, int offset);
	
	/**
	 * Compares this Blob to another Blob, in lexicographic order sorting by first
	 * bytes (unsigned).
	 * 
	 * Note: This means that compareTo does not precisely match equality, because
	 * specialised Blob types may be lexicographically equal but represent different values.
	 */
	@Override
	public int compareTo(ABlobLike<?> b) {
		if (this == b) return 0;
		long alength = this.count();
		long blength = b.count();
		long compareLength = Math.min(alength, blength);
		for (long i = 0; i < compareLength; i++) {
			int c = (0xFF & byteAtUnchecked(i)) - (0xFF & b.byteAtUnchecked(i));
			if (c > 0) return 1;
			if (c < 0) return -1;
		}
		if (alength > compareLength) return 1; // this is bigger
		if (blength > compareLength) return -1; // b is bigger
		return 0;
	}
	
	/**
	 * Gets a chunk of this Blob, as a canonical Blob up to the maximum chunk size.
	 * Returns empty Blob if and only if referencing the end of a Blob with fully packed chunks
	 * 
	 * @param i Index of chunk
	 * @return A Blob containing the specified chunk data.
	 */
	public abstract Blob getChunk(long i);
	
	/**
	 * Prints this Blob in a readable Hex representation, typically in the format "0x01abcd...."
	 * 
	 * Subclasses may override this if they require a different representation.
	 */
	@Override
	public boolean print(BlobBuilder bb, long limit) {
		bb.append(Strings.HEX_PREFIX);
		return appendHex(bb,limit-bb.count());
	}

	/**
	 * Gets a byte buffer containing this Blob's raw data. Will have remaining bytes
	 * equal to this Blob's size.
	 * 
	 * @return A ByteBuffer containing the Blob's data.
	 */
	public abstract ByteBuffer getByteBuffer();

	@Override
	public void validate() throws InvalidDataException {
		super.validate();
	}

	@Override
	public void validateCell() throws InvalidDataException {
		if (count() < 0) throw new InvalidDataException("Negative blob length", this);
	}



	
	/**
	 * Writes this Blob's encoding to a byte array, excluding the tag byte
	 *
	 * @param bs A byte array to which to write the encoding
	 * @param pos The offset into the byte array
	 * @return New position after writing
	 */
	public abstract int encodeRaw(byte[] bs, int pos);
	

	
	@Override
	public int hashCode() {
		// note: We use a salted hash of the last bytes for blobs. 
		// SECURITY: This is decent for small blobs, DoS risk for user generated large blobs. Be careful putting large keys in Java HashMaps.....
		return Bits.hash32(longValue());
	}

	@Override
	public boolean isRegularBlob() {
		return true;
	}

	@Override public boolean isCVMValue() {
		return true;
	}

	/**
	 * Tests if this Blob has exactly the same bytes as another Blob
	 * @param b Blob to compare with
	 * @return True if byte content is exactly equal, false otherwise
	 */
	public abstract boolean equalsBytes(ABlob b);

	public short shortAt(long i) {
		byte hi=byteAt(i);
		byte lo=byteAt(i+1);
		return (short)((hi<<8)|(lo&0xFF));
	}

	/**
	 * Returns true if this is a fully packed set of chunks
	 * @return True if fully packed, false otherwise
	 */
	public abstract boolean isChunkPacked();

	/**
	 * Returns true if this is a fully packed set of chunks
	 * @return True if fully packed, false otherwise
	 */
	public abstract boolean isFullyPacked();


}
