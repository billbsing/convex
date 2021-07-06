package convex.core.lang;

import convex.core.data.*;

import java.util.ArrayList;

public class ScryptNext extends Reader {

	// Use a ThreadLocal reader because instances are not thread safe
	private static final ScryptNext syntaxReader = new ScryptNext();

	/**
	* Gets a thread-local instance of the Scrypt Reader
	* @return
	*/
	public static ScryptNext instance() {
		return syntaxReader;
	}

}
