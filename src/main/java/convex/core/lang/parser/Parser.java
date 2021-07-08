package convex.core.lang.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import convex.core.data.ACell;
import convex.core.data.Address;
import convex.core.data.Blob;
import convex.core.data.Keyword;
import convex.core.data.Lists;
import convex.core.data.Maps;
import convex.core.data.Sets;
import convex.core.data.Strings;
import convex.core.data.Symbol;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMBool;
import convex.core.data.prim.CVMChar;
import convex.core.data.prim.CVMDouble;
import convex.core.data.prim.CVMLong;
import convex.core.lang.Symbols;



public class Parser {


	private static final String SYMBOL_FIRST_CHAR = "[a-zA-Z\\.\\*\\+!\\-_?\\$%&=<>]";
	private static final String SYMBOL_NON_NUMERIC_CHAR = "[a-zA-Z\\.\\*\\+!\\-_?\\$%&=<>:#]";
	private static final String SYMBOL_FOLLOWING_CHAR = "[0-9a-zA-Z\\.\\*\\+!\\-_?\\$%&=<>:#]";
	private static final Pattern SYMBOL_PATTERN_1 = Pattern.compile(SYMBOL_FIRST_CHAR + SYMBOL_FOLLOWING_CHAR + "*");
	private static final Pattern SYMBOL_PATTERN_2 = Pattern.compile("[\\.+-]" + SYMBOL_NON_NUMERIC_CHAR + "+" + SYMBOL_FOLLOWING_CHAR + "*");
	private static final Pattern SYMBOL_PATTERN_3 = Pattern.compile("[/.]");

	private static final Pattern KEYWORD_PATTERN = Pattern.compile(":" + SYMBOL_FIRST_CHAR + SYMBOL_FOLLOWING_CHAR + "*");

	private static final Pattern SYMBOL_LOOKUP_ADDRESS_MATCH = Pattern.compile("#([0-9]+)/(" + SYMBOL_FIRST_CHAR + SYMBOL_FOLLOWING_CHAR + "*)");
	private static final Pattern SYMBOL_LOOKUP_MATCH = Pattern.compile(
		"(" +
		SYMBOL_FIRST_CHAR + SYMBOL_FOLLOWING_CHAR +
		"*)/(" +
		SYMBOL_FIRST_CHAR + SYMBOL_FOLLOWING_CHAR +
		"*)");
	private static final Pattern DOUBLE_PATTERN_1 = Pattern.compile("[+\\-]?[0-9]+\\.[0-9]+");
	private static final Pattern DOUBLE_PATTERN_2 = Pattern.compile("[+\\-]?[0-9]+\\.[eE]\\-?[0-9]+");
	private static final Pattern LONG_PATTERN = Pattern.compile("[+\\-]?[0-9]+");
	private static final Pattern ADDRESS_PATTERN = Pattern.compile("#[0-9]+");
	private static final Pattern HEX_MATCH = Pattern.compile("0[xX]([0-9a-fA-F]+)");
	private static final Pattern BOOLEAN_PATTERN = Pattern.compile("(true)|(false)");
	private static final Pattern SPECIAL_TEXT_PATTERN = Pattern.compile("\\\\[0-9a-zA-Z]+");
	private static final Pattern SPECIAL_NEWLINE_PATTERN = Pattern.compile("\\\\newline");
	private static final Pattern SPECIAL_SPACE_PATTERN = Pattern.compile("\\\\space");
	private static final Pattern SPECIAL_TAB_PATTERN = Pattern.compile("\\\\tab");
	private static final Pattern SPECIAL_FORM_FEED_PATTERN = Pattern.compile("\\\\formfeed");
	private static final Pattern SPECIAL_BACKSPACE_PATTERN = Pattern.compile("\\\\backsapce");
	private static final Pattern SPECIAL_RETURN_PATTERN = Pattern.compile("\\\\return");
	private static final Pattern SPECIAL_UNICODE_MATCH = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
	private static final Pattern SPECIAL_ANY_PATTERN = Pattern.compile("\\\\[0-9a-zA-Z]");
	private static final Pattern MATH_NAN = Pattern.compile("##NaN");
	private static final Pattern MATH_POS_INF = Pattern.compile("##Inf");
	private static final Pattern MATH_NEG_INF = Pattern.compile("##-Inf");
	private static final Pattern MATH_NIL_PATTERN = Pattern.compile("nil");



	protected List<Token> tokenList;
	protected Node rootNode;
	protected Node topNode;

	private Parser(List<Token> tokenList) {
		this.tokenList = tokenList;
		this.rootNode = Node.create();
	}


	public static Parser create(List<Token> tokenList) {
		return new Parser(tokenList);
	}

	public static Error createError(String message, Token token) {
		StringBuffer buffer = new StringBuffer(message);
		if (token != null) {
			TokenPosition position = token.getPosition();
			buffer.append(" at Line " + position.getRow() + 1 + ", Column " + position.getColumn() + 1);
		}
		return new Error(buffer.toString());
	}

	protected Node decodeKeyword(Node parentNode, Token token) {
		Node node = null;
		if (token.isFirstChar(':')) {
			if (!token.isMatch(KEYWORD_PATTERN)) {
				throw Parser.createError("Invalid keyword '" + token.getValue(), token);
			}
			node = Node.create(parentNode, token);
			node.setValue(Keyword.create(token.subString(1)));
		}
		return node;
	}

	protected Node decodeMath(Node parentNode, Token token) {
		Node node = null;
		if (token.isMatch(MATH_NAN)) {
			node = Node.create(parentNode, token);
			node.setValue(CVMDouble.NaN);
		}
		else if  (token.isMatch(MATH_POS_INF)) {
			node = Node.create(parentNode, token);
			node.setValue(CVMDouble.POSITIVE_INFINITY);
		}
		else if  (token.isMatch(MATH_NEG_INF)) {
			node = Node.create(parentNode, token);
			node.setValue(CVMDouble.NEGATIVE_INFINITY);
		}
		else if (token.isMatch(MATH_NIL_PATTERN)) {
			node = Node.create(parentNode, token);
			node.setValue(null);
		}
		return node;
	}

	protected Node decodeSpecialChars(Node parentNode, Token token) {
		Node node = null;
		// System.out.println("special char " + token.getValue());
		if (token.isMatch(SPECIAL_TEXT_PATTERN)) {
			if (token.isMatch(SPECIAL_NEWLINE_PATTERN)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create('\n'));
			}
			else if (token.isMatch(SPECIAL_SPACE_PATTERN)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create(' '));
			}
			else if (token.isMatch(SPECIAL_TAB_PATTERN)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create('\t'));
			}
			else if (token.isMatch(SPECIAL_FORM_FEED_PATTERN)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create('\f'));
			}
			else if (token.isMatch(SPECIAL_BACKSPACE_PATTERN)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create('\b'));
			}
			else if (token.isMatch(SPECIAL_RETURN_PATTERN)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create('\r'));
			}
			else if (token.isMatch(SPECIAL_UNICODE_MATCH)) {
				Matcher matcher = token.matcher(SPECIAL_UNICODE_MATCH);
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create(Long.parseLong(matcher.group(1), 16)));
			}
			if (token.isMatch(SPECIAL_ANY_PATTERN)) {
				System.out.println("special char " + token.getValue());
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create(token.charAt(1)));
			}
		}
		return node;
	}
	protected Node decodeNumbers(Node parentNode, Token token) {
		Node node = null;
		if (token.isMatch(LONG_PATTERN) || token.isMatch(ADDRESS_PATTERN)) {
			node = Node.create(parentNode, token);
			node.setValue(CVMLong.parse(token.getValue()));
		}
		else if (token.isMatch(DOUBLE_PATTERN_1) || token.isMatch(DOUBLE_PATTERN_2)) {
			node = Node.create(parentNode, token);
			node.setValue(CVMDouble.parse(token.getValue()));
		}
		else if (token.isMatch(HEX_MATCH) ) {
			Matcher matcher = token.matcher(HEX_MATCH);
			node = Node.create(parentNode, token);
			node.setValue(Blob.fromHex(matcher.group(1)));
		}
		else if (token.isMatch(BOOLEAN_PATTERN) ) {
			node = Node.create(parentNode, token);
			if (token.getValue() == "true") {
				node.setValue(CVMBool.TRUE);
			}
			if (token.getValue() == "false") {
				node.setValue(CVMBool.FALSE);
			}
		}
		return node;
	}

	protected Node decodeConstant(Node parentNode, Token token) {
		Node node = decodeNumbers(parentNode, token);
		if (node != null) {
			return node;
		}
		node = decodeSpecialChars(parentNode, token);
		if (node != null) {
			return node;
		}
		node = decodeMath(parentNode, token);
		if (node != null) {
			return node;
		}
		node = decodeKeyword(parentNode, token);
		return node;
	}

	protected Node decodeSymbol(Node parentNode, Token token) {
		Node node = null;
		if (token.isMatch(SYMBOL_LOOKUP_ADDRESS_MATCH)) {
			Matcher matcher = token.matcher(SYMBOL_LOOKUP_ADDRESS_MATCH);
			// System.out.println("symbol lookup address " + token.getValue() + " " + matcher.group(1));
			Symbol symbol = Symbol.create(matcher.group(2));
			if (symbol == null) {
				throw Parser.createError("invalid symbol " + matcher.group(2), token);
			}
			node = Node.create(parentNode, token);
			node.setValue(Lists.of(Symbols.LOOKUP, Address.create(Long.parseLong(matcher.group(1))), symbol));
		}
		else if (token.isMatch(SYMBOL_LOOKUP_MATCH)) {
			// System.out.println("symbol lookup " + token.getValue());
			Matcher matcher = token.matcher(SYMBOL_LOOKUP_MATCH);
			Symbol symbol1 = Symbol.create(matcher.group(1));
			if (symbol1 == null) {
				throw Parser.createError("invalid symbol " + matcher.group(1), token);
			}
			Symbol symbol2 = Symbol.create(matcher.group(2));
			if (symbol2 == null) {
				throw Parser.createError("invalid symbol " + matcher.group(2), token);
			}
			node = Node.create(parentNode, token);
			node.setValue(Lists.of(Symbols.LOOKUP, symbol1, symbol2));
		}
		else if (token.isMatch(SYMBOL_PATTERN_1) || token.isMatch(SYMBOL_PATTERN_2) || token.isMatch(SYMBOL_PATTERN_3)) {
			// System.out.println("symbol " + token.getValue());
			Symbol symbol = Symbol.create(token.getValue());
			if (symbol == null) {
				throw Parser.createError("invalid symbol " + token.getValue(), token);
			}
			node = Node.create(parentNode, token);
			node.setValue(symbol);
		}
		return node;
	}

	protected Node decodeValue(Node parentNode, Token token) {
		Node node = null;
		node = decodeConstant(parentNode, token);
		if (node != null) {
			return node;
		}
		node = decodeSymbol(parentNode, token);
		return node;
	}

	public void parse() {
		Node currentNode = rootNode;
		Node node;
		ListIterator<Token> tokenIterator = tokenList.listIterator();
		while (tokenIterator.hasNext()) {
			Token token = tokenIterator.next();
			switch(token.getType()) {
				case LIST_OPEN:
				case VECTOR_OPEN:
				case MAP_OPEN:
				case SET_OPEN:
				case META_OPEN:
					node = Node.create(currentNode, token);
					currentNode.addNode(node);
					currentNode = node;
					break;
				case LIST_CLOSE:
					if (currentNode.getToken().isType(Token.Type.LIST_OPEN)) {
						// got the list back
						currentNode.setValue(Lists.of(currentNode.getValues()));
					}
					else {
						throw Parser.createError("missing closing list bracket ')'", currentNode.getToken());
					}
					currentNode = currentNode.getParentNode();
					break;
				case VECTOR_CLOSE:
					if (currentNode.getToken().isType(Token.Type.VECTOR_OPEN)) {
						// got the list back
						currentNode.setValue(Vectors.of(currentNode.getValues()));
					}
					else {
						throw Parser.createError("missing closing vector bracket ']'", currentNode.getToken());
					}
					currentNode = currentNode.getParentNode();
					break;
				case MAP_META_SET_CLOSE:
					if (currentNode.getToken().isType(Token.Type.MAP_OPEN)) {
						currentNode.setValue(Maps.of(currentNode.getValues()));
					}
					else if (currentNode.getToken().isType(Token.Type.META_OPEN)) {
						currentNode.setValue(Maps.of(currentNode.getValues()));
					}
					else if (currentNode.getToken().isType(Token.Type.SET_OPEN)) {
						currentNode.setValue(Sets.of(currentNode.getValues()));
					}
					else {
						throw Parser.createError("missing closing map or set bracket '}'", currentNode.getToken());
					}
					break;
				case STRING:
					node = Node.create(currentNode, token);
					node.setValue(Strings.create(token.toString()));
					currentNode.addNode(node);
					break;
				case QUOTE:
					node = Node.create(currentNode, token);
					node.setValue(Symbols.QUOTE);
					currentNode.addNode(node);
					break;
				case QUASI_QUOTE:
					node = Node.create(currentNode, token);
					node.setValue(Symbols.QUASIQUOTE);
					currentNode.addNode(node);
					break;
				case UNQUOTE:
					node = Node.create(currentNode, token);
					node.setValue(Symbols.UNQUOTE);
					currentNode.addNode(node);
					break;
				case UNQUOTE_SPLICING:
					node = Node.create(currentNode, token);
					node.setValue(Symbols.UNQUOTE_SPLICING);
					currentNode.addNode(node);
					break;
				case VALUE:
					node = decodeValue(currentNode, token);
					if ( node != null) {
						currentNode.addNode(node);
					}
					break;
			}
		}
	}

	public ACell generate() {
		if (rootNode.getNodeList().size() == 1) {
			return rootNode.getNode(0).getValue();
		}
		return Lists.of(rootNode.getValues());
	}
}
