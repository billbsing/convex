package convex.core.lang;

import java.io.IOException;
import java.lang.StringBuffer;
import java.util.ArrayList;
import java.util.ListIterator;

import convex.core.data.ACell;
import convex.core.data.AHashMap;
import convex.core.data.AList;
import convex.core.data.AMap;
import convex.core.data.ASequence;
import convex.core.data.Address;
import convex.core.data.Blob;
import convex.core.data.Keyword;
import convex.core.data.Keywords;
import convex.core.data.Lists;
import convex.core.data.Maps;
import convex.core.data.Sets;
import convex.core.data.Strings;
import convex.core.data.Symbol;
import convex.core.data.Syntax;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMBool;
import convex.core.data.prim.CVMChar;
import convex.core.data.prim.CVMDouble;
import convex.core.data.prim.CVMLong;
import convex.core.exceptions.ParseException;
import convex.core.lang.parser.Parser;
import convex.core.lang.parser.Tokenizer;
import convex.core.lang.parser.Token;
import convex.core.lang.parser.TokenPosition;
import convex.core.util.Utils;

/**
 *
 */
public class Reader  {

	protected static StringBuffer buffer;


	public static <R extends ACell> R read(String source) {
		System.out.println(source);
		buffer = new StringBuffer(source);
		Tokenizer tokenizer = Tokenizer.create(source);
		Parser parser = Parser.create(tokenizer.getTokenList());
		tokenizer.printOut();
		return (R) CVMChar.create(' ');
	}

	public static ACell readResource(String path)  {
		String source;
		try {
			source = Utils.readResourceAsString(path);
		} catch (IOException e) {
			throw Utils.sneakyThrow(e);
		}
		return read(source);
	}

	public static ACell readResourceAsData(String path) throws IOException {
		String source = Utils.readResourceAsString(path);
		return read(source);
	}


	public static AList<ACell> readAll(String source) {
		AList<ACell> list = (AList<ACell>) read(source);
		return list;
	}

}

