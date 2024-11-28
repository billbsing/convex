package convex.core.data;

import convex.core.exceptions.BadFormatException;
import convex.core.lang.Core;

/**
 * Encoder for CVM values and data structures
 */
public class CVMEncoder extends CAD3Encoder {

	public static final CVMEncoder INSTANCE = new CVMEncoder();

	@Override
	public ACell read(Blob encoding,int offset) throws BadFormatException {
		return super.read(encoding,offset);
	}

	protected ACell readExtension(byte tag, Blob blob, int offset) throws BadFormatException {
		// We expect a VLQ Count following the tag
		long code=Format.readVLQCount(blob,offset+1);
		if (tag == Tag.CORE_DEF) return Core.fromCode(code);
		
		return ExtensionValue.create(tag, code);
	}
}
