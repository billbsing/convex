package convex.core.lang.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;

import convex.core.data.ACell;
import convex.core.data.Blob;
import convex.core.data.Keyword;
import convex.core.data.Lists;
import convex.core.data.Strings;
import convex.core.data.Symbol;
import convex.core.data.Vectors;
import convex.core.data.prim.CVMBool;
import convex.core.data.prim.CVMChar;
import convex.core.data.prim.CVMDouble;
import convex.core.data.prim.CVMLong;



public class Parser {

	private static final Pattern KEYWORD_MATCH = Pattern.compile(":[a-zA-Z][a-zA-Z0-9\\-/\\.]+[a-zA-Z0-9]");

	private static final Pattern SYMBOL_MATCH = Pattern.compile("[a-zA-Z][a-zA-Z0-9+\\-/\\.]+[a-zA-Z0-9]");

	private static final Pattern DOUBLE_MATCH = Pattern.compile("[+\\-]?[0-9\\.]+[eE+\\-]?[0-9]+");

	private static final Pattern LONG_MATCH = Pattern.compile("[+\\-]?[0-9]+");

	private static final Pattern ADDRESS_MATCH = Pattern.compile("#[0-9]+");

	private static final Pattern HEX_MATCH = Pattern.compile("0[xX][0-9a-fA-F]+");

	private static final Pattern BOOLEAN_MATCH = Pattern.compile("(true)|(false)");

	private static final Pattern SPECIAL_TEXT_MATCH = Pattern.compile("\\\\[a-zA-Z][a-fA-F0-9]+");
	private static final Pattern SPECIAL_NEWLINE_MATCH = Pattern.compile("\\\\newline");
	private static final Pattern SPECIAL_SPACE_MATCH = Pattern.compile("\\\\space");
	private static final Pattern SPECIAL_TAB_MATCH = Pattern.compile("\\\\tab");
	private static final Pattern SPECIAL_FORM_FEED_MATCH = Pattern.compile("\\\\formfeed");
	private static final Pattern SPECIAL_BACKSPACE_MATCH = Pattern.compile("\\\\backsapce");
	private static final Pattern SPECIAL_RETURN_MATCH = Pattern.compile("\\\\return");
	private static final Pattern SPECIAL_UNICODE_MATCH = Pattern.compile("\\\\u[a-fA-F0-9]{4}");
	private static final Pattern SPECIAL_ANY_MATCH = Pattern.compile("\\\\.");


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
			if (!token.isMatch(KEYWORD_MATCH)) {
				throw Parser.createError("Invalid keyword '" + token.getValue(), token);
			}
			node = Node.create(parentNode, token);
			node.setValue(Keyword.create(token.subString(1)));
		}
		return node;
	}

	protected Node decodeSpecialChars(Node parentNode, Token token) {
		Node node = null;
		if (token.isMatch(SPECIAL_TEXT_MATCH)) {
			if (token.isMatch(SPECIAL_NEWLINE_MATCH)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create('\n'));
			}
			else if (token.isMatch(SPECIAL_SPACE_MATCH)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create(' '));
			}
			else if (token.isMatch(SPECIAL_TAB_MATCH)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create('\t'));
			}
			else if (token.isMatch(SPECIAL_FORM_FEED_MATCH)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create('\f'));
			}
			else if (token.isMatch(SPECIAL_BACKSPACE_MATCH)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create('\b'));
			}
			else if (token.isMatch(SPECIAL_RETURN_MATCH)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create('\r'));
			}
			else if (token.isMatch(SPECIAL_UNICODE_MATCH)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create(Long.parseLong(token.subString(2), 16)));
			}
			if (token.isMatch(SPECIAL_ANY_MATCH)) {
				node = Node.create(parentNode, token);
				node.setValue(CVMChar.create(token.charAt(1)));
			}
		}
		return node;
	}

	protected Node decodeConstant(Node parentNode, Token token) {
		Node node = null;
		if (token.isMatch(LONG_MATCH) || token.isMatch(ADDRESS_MATCH)) {
			node = Node.create(parentNode, token);
			node.setValue(CVMLong.parse(token.getValue()));
		}
		else if (token.isMatch(DOUBLE_MATCH)) {
			node = Node.create(parentNode, token);
			node.setValue(CVMDouble.parse(token.getValue()));
		}
		else if (token.isMatch(HEX_MATCH) ) {
			node = Node.create(parentNode, token);
			node.setValue(Blob.fromHex(token.getValue()));
		}
		else if (token.isMatch(BOOLEAN_MATCH) ) {
			node = Node.create(parentNode, token);
			if (token.getValue() == "true") {
				node.setValue(CVMBool.TRUE);
			}
			if (token.getValue() == "false") {
				node.setValue(CVMBool.FALSE);
			}
		}
		if (node != null) {
			return node;
		}
		node = decodeSpecialChars(parentNode, token);
		if (node != null) {
			return node;
		}
		node = decodeKeyword(parentNode, token);
		return node;
	}

	protected Node decodeSymbol(Node parentNode, Token token) {
		Node node = null;
		if ( token.isMatch(SYMBOL_MATCH)) {
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
				case STRING:
					node = Node.create(currentNode, token);
					node.setValue(Strings.create(token.toString()));
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
