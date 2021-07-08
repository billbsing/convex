package convex.core.lang;

import static org.junit.jupiter.api.Assertions.*;
import static convex.test.Assertions.*;

import org.junit.jupiter.api.Test;
// import org.parboiled.Parboiled;

import convex.core.data.AList;
import convex.core.data.AVector;
import convex.core.data.Address;
import convex.core.data.Blob;
import convex.core.data.Blobs;
import convex.core.data.Keyword;
import convex.core.data.Keywords;
import convex.core.data.Lists;
import convex.core.data.Maps;
import convex.core.data.Strings;
import convex.core.data.Symbol;
import convex.core.data.Syntax;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMBool;
import convex.core.data.prim.CVMDouble;
import convex.core.exceptions.ParseException;
import convex.test.Samples;

public class ReaderTest {

	@Test
	public void testVectors() {
		assertSame(Vectors.empty(), Reader.read("[]"));
		assertSame(Vectors.empty(), Reader.read(" [ ] "));

		assertEquals(Vectors.of(1L,-2L), Reader.read("[1 -2]"));

		assertEquals(Vectors.of(Samples.FOO), Reader.read(" [ :foo ] "));
		assertEquals(Vectors.of(Vectors.empty()), Reader.read(" [ [] ] "));
	}

	@Test
	public void testKeywords() {
		assertEquals(Samples.FOO, Reader.read(":foo"));
		assertEquals(Keyword.create("foo.bar"), Reader.read(":foo.bar"));

		// : is currently a valid symbol character
		assertEquals(Keyword.create("foo:bar"), Reader.read(":foo:bar"));

	}

	@Test
	public void testBadKeywords() {
		assertThrows(Error.class, () -> Reader.read(":"));
	}

	@Test
	public void testComment() {
		assertCVMEquals(1L, Reader.read(";this is a comment\n 1 \n"));
		assertCVMEquals(2L, Reader.read("#_foo 2"));
		assertCVMEquals(3L, Reader.read("3 #_foo"));
	}

	@Test
	public void testReadAll() {
		assertSame(Lists.empty(), Reader.readAll(""));
		assertSame(Lists.empty(), Reader.readAll("  "));
		assertEquals(Samples.FOO, Reader.readAll(" :foo ").get(0).getValue());
		assertEquals(Symbol.create("+"), Reader.readAll("+ 1").get(0).getValue());
	}

	@Test
	public void testReadSymbol() {
		assertEquals(Symbols.FOO, Reader.readSymbol("foo"));
		assertThrows(Error.class, () -> Reader.readSymbol(""));
		assertThrows(Error.class, () -> Reader.readSymbol("1"));
	}

	@Test
	public void testSymbols() {
		assertEquals(Symbols.FOO, Reader.read("foo"));
		assertEquals(Lists.of(Symbols.LOOKUP,Address.create(666),Symbols.FOO), Reader.read("#666/foo"));

		assertEquals(Lists.of(Symbol.create("+"), 1L), Reader.read("(+ 1)"));
		assertEquals(Lists.of(Symbol.create("+a")), Reader.read("( +a )"));
		assertEquals(Lists.of(Symbol.create("/")), Reader.read("(/)"));
		assertEquals(Lists.of(Symbols.LOOKUP,Symbols.FOO,Symbols.BAR), Reader.read("foo/bar"));
		assertEquals(Symbol.create("a*+!-_?<>=!"), Reader.read("a*+!-_?<>=!"));
		assertEquals(Symbol.create("foo.bar"), Reader.read("foo.bar"));
		assertEquals(Symbol.create(".bar"), Reader.read(".bar"));

		// Interpret leading dot as symbols always. Addresses Issue #65
		assertEquals(Symbol.create(".56"), Reader.read(".56"));


		// namespaces cannot themselves be qualified
		assertThrows(ParseException.class,()->Reader.read("a/b/c"));

		// Bad address parsing
		assertThrows(ParseException.class,()->Reader.read("#-1/foo"));

		// too long symbol names
		assertThrows(ParseException.class,()->Reader.read("abcdefghijklmnopabcdefghijklmnopabcdefghijklmnopabcdefghijklmnopabcdefghijklmnop"));
		assertThrows(ParseException.class,()->Reader.read("abcdefghijklmnopabcdefghijklmnopabcdefghijklmnopabcdefghijklmnopabcdefghijklmnop/a"));
		assertThrows(ParseException.class,()->Reader.read("a/abcdefghijklmnopabcdefghijklmnopabcdefghijklmnopabcdefghijklmnopabcdefghijklmnop"));

	}

	@Test
	public void testSymbolsRegressionCases() {
		assertEquals(Symbol.create("nils"), Reader.read("nils"));

		// symbol starting with a boolean value
		assertEquals(Symbol.create("falsey"), Reader.read("falsey"));
		assertEquals(Symbol.create("true-exp"), Reader.read("true-exp"));
	}

	@Test
	public void testChar() {
		assertCVMEquals('A', Reader.read("\\A"));
		assertCVMEquals('a', Reader.read("\\u0061"));
		assertCVMEquals(' ', Reader.read("\\space"));
		assertCVMEquals('\t', Reader.read("\\tab"));
		assertCVMEquals('\n', Reader.read("\\newline"));
		assertCVMEquals('\f', Reader.read("\\formfeed"));
		assertCVMEquals('\b', Reader.read("\\backspace"));
		assertCVMEquals('\r', Reader.read("\\return"));
	}

	@Test
	public void testNumbers() {
		assertCVMEquals(1L, Reader.read("1"));
		assertCVMEquals(2.0, Reader.read("2.0"));

		// scientific notation
		assertCVMEquals(2.0, Reader.read("2.0e0"));
		assertCVMEquals(20.0, Reader.read("2.0e1"));
		assertCVMEquals(0.2, Reader.read("2.0e-1"));
		assertCVMEquals(12.0, Reader.read("12e0"));

		assertThrows(Error.class, () -> Reader.read("2.0e0.1234"));
		// assertNull( Reader.read("[2.0e0.1234]"));
		assertThrows(Error.class, () -> Reader.read("[2.0e0.1234]")); // Issue #70

		// metadata ignored
		assertEquals(Syntax.create(RT.cvm(3.23),Maps.of(Keywords.FOO, CVMBool.TRUE)), Reader.read("^:foo 3.23"));
	}

	@Test
	public void testSpecialNumbers() {
		assertEquals(CVMDouble.NaN, Reader.read("##NaN"));
		assertEquals(CVMDouble.POSITIVE_INFINITY, Reader.read("##Inf "));
		assertEquals(CVMDouble.NEGATIVE_INFINITY, Reader.read(" ##-Inf"));
	}

	@Test
	public void testHexBlobs() {
		assertEquals(Blobs.fromHex("cafebabe"), Reader.read("0xcafebabe"));
		assertEquals(Blobs.fromHex("0aA1"), Reader.read("0x0Aa1"));
		assertEquals(Blob.EMPTY, Reader.read("0x"));

		// TODO: figure out the edge case
		assertThrows(Error.class, () -> Reader.read("0x1"));
		//assertThrows(Error.class, () -> Reader.read("[0x1]")); // odd number of hex digits

		assertThrows(Error.class, () -> Reader.read("0x123")); // odd number of hex digits
	}

	@Test
	public void testNil() {
		assertNull(Reader.read("nil"));

		// metadata on null
		assertEquals(Syntax.create(null),Reader.read("^{} nil"));
	}

	@Test
	public void testStrings() {
		assertSame(Strings.empty(), Reader.read("\"\""));
		assertEquals(Strings.create("bar"), Reader.read("\"bar\""));
		assertEquals(Vectors.of(Strings.create("bar")), Reader.read("[\"bar\"]"));
		assertEquals(Strings.create("\"bar\""), Reader.read("\"\\\"bar\\\"\""));

	}

	@Test
	public void testList() {
		assertSame(Lists.empty(), Reader.read(" ()"));
		assertEquals(Lists.of(1L, 2L), Reader.read("(1 2)"));
		assertEquals(Lists.of(Vectors.empty()), Reader.read(" ([] )"));
	}

	@Test
	public void testNoWhiteSpace() {
		assertEquals(Lists.of(Vectors.empty(), Vectors.empty()), Reader.read("([][])"));
		assertEquals(Lists.of(Vectors.empty(), 13L), Reader.read("([]13)"));
		assertEquals(Lists.of(Symbols.SET, Vectors.empty()), Reader.read("(set[])"));
	}

	@Test
	public void testMaps() {
		assertSame(Maps.empty(), Reader.read("{}"));
		assertEquals(Maps.of(1L, 2L), Reader.read("{1,2}"));
		assertEquals(Maps.of(Samples.FOO, Samples.BAR), Reader.read("{:foo :bar}"));
	}

	@Test
	public void testMapError() {
//		assertThrows(ParseException.class,()->Reader.read("{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}"));
	}

	@Test
	public void testQuote() {
		assertEquals(Lists.of(Symbols.QUOTE, 1L), Reader.read("'1"));
		assertEquals(Lists.of(Symbols.QUOTE, Lists.of(Symbols.QUOTE, Vectors.empty())), Reader.read("''[]"));

		assertEquals(Lists.of(Symbols.QUOTE,Lists.of(Symbols.UNQUOTE,Symbols.FOO)),Reader.read("'~foo"));

	}

	@Test
	public void testRules() {
		Reader reader = Parboiled.createParser(Reader.class, false);
		assertCVMEquals(1L, Reader.doParse(reader.Long(), "1  "));
	}

	@Test
	public void testWrongSizeMaps() {
		assertThrows(ParseException.class, () -> Reader.read("{:foobar}"));
	}

	@Test
	public void testParsingNothing() {
		assertThrows(ParseException.class, () -> Reader.read("  "));
	}

	@Test
	public void testSyntaxReader() {
		assertEquals(Syntax.class, Reader.readSyntax("nil").getClass());
		assertEquals(Syntax.create(RT.cvm(1L)), Reader.readSyntax("1").withoutMeta());
		assertEquals(Syntax.create(Symbols.FOO), Reader.readSyntax("foo").withoutMeta());
		assertEquals(Syntax.create(Keywords.FOO), Reader.readSyntax(":foo").withoutMeta());
	}

	@Test
	public void testSyntaxReaderExample() {
		String src = "[1 2 nil '(a b) :foo 2 \\a \"bar\" #{} {1 2 3 4}]";
		Syntax s = Reader.readSyntax(src);
		AVector<Syntax> v = s.getValue();
		Syntax v1 = v.get(1);
		assertCVMEquals(2L, v1.getValue());
		//assertEquals(3L, v1.getStart());
		//assertEquals(4L, v1.getEnd());

		//assertEquals(src, s.getSource());
	}

	@Test
	public void testReadMetadata() {
		assertEquals(Syntax.create(Keywords.FOO),Reader.read("^{} :foo"));
	}


	@Test
	public void testMetadata() {
		assertCVMEquals(Boolean.TRUE, Reader.readSyntax("^:foo a").getMeta().get(Keywords.FOO));

		{
			Syntax def=Reader.readAllSyntax("(def ^{:foo 2} a 1)").get(0);
			AList<Syntax> form=def.getValue();
			assertCVMEquals(2L, form.get(1).getMeta().get(Keywords.FOO));
		}

		// TODO: Decide how to handle values within meta - unwrap Syntax Objects?
		assertCVMEquals(Boolean.FALSE, Reader.readSyntax("^{:foo false} a").getMeta().get(Keywords.FOO));
		assertEquals(Vectors.of(1L, 2L), Reader.readSyntax("^{:foo [1 2]} a").getMeta().get(Keywords.FOO));
	}
}
