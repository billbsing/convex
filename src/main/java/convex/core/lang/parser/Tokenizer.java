package convex.core.lang.parser;


import java.lang.StringBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.ListIterator;


public class Tokenizer {

	private static final String WHITESPACE = " \t\f";

	private static final char TAB_CHAR = '\t';

	private static final int TAB_LENGTH = 4;

	private static final String NEW_LINE = "\r\n";

	private static final String COMMENT_LINE = ";";

	private static final char STRING_CHAR = '"';

	private static final EnumMap<Token.Type, String> seperators = new EnumMap(Token.Type.class);


	// 	"[](){}'`~@";

	StringBuffer buffer;
	TokenPosition position;
	List<Token> tokenList = new ArrayList<Token>();

	private Tokenizer(String source) {
		buffer = new StringBuffer(source);
		position = TokenPosition.create();
		seperators.put(Token.Type.COMMENT, ";");
		seperators.put(Token.Type.COMMENT_INLINE, "#_");
		seperators.put(Token.Type.STRING_OPEN_CLOSE, "\"");
		seperators.put(Token.Type.LIST_OPEN, "(");
		seperators.put(Token.Type.LIST_CLOSE, ")");
		seperators.put(Token.Type.VECTOR_OPEN, "[");
		seperators.put(Token.Type.VECTOR_CLOSE, "]");
		seperators.put(Token.Type.SET_OPEN, "#{");
		seperators.put(Token.Type.META_OPEN, "^{");
		seperators.put(Token.Type.MAP_OPEN, "{");
		seperators.put(Token.Type.MAP_META_SET_CLOSE, "}");
		seperators.put(Token.Type.QUOTE, "'");
		seperators.put(Token.Type.QUASI_QUOTE, "`");
		seperators.put(Token.Type.UNQUOTE, "~");
		seperators.put(Token.Type.UNQUOTE_SPLICING, "~@");

		read();
	}

	public static Tokenizer create(String source) {
		return new Tokenizer(source);
	}

	protected void readUntil(Token token, String stopString) {
		while (position.getIndex() < buffer.length()) {
			char value = buffer.charAt(position.getIndex());
			if (stopString.indexOf(value) >= 0) {
				break;
			}
			token.appendChar(value);
			position.next();
			position.move(1, 0);
		}
	}

	protected void pushToken(Token token) {
		if (token!= null) {
			tokenList.add(token);
		}
	}

	protected void readStringToken(Token token) {
		pushToken(token);
		position.next();
		position.move(1, 0);

		token = Token.create(position, Token.Type.STRING);
		while (position.getIndex() < buffer.length()) {
			char value = buffer.charAt(position.getIndex());
			if (STRING_CHAR == value) {
				// push the string value as a token
				pushToken(token);
				token = Token.create(position, Token.Type.STRING_OPEN_CLOSE);
				token.appendChar(value);
				pushToken(token);
				position.next();
				position.move(1, 0);
				break;
			}
			// TODO escape sequences and unicode?
			token.appendChar(value);
			position.next();
			position.move(1, 0);
		}
	}

	protected Token matchTokenSeperator(int positionIndex) {
		Token token = null;
		String foundMatch = null;
		Token.Type foundTokenType = Token.Type.NONE;
		for (Token.Type tokenType : seperators.keySet()) {
			String matchValue = seperators.get(tokenType);
			if (buffer.indexOf(matchValue, positionIndex) == positionIndex) {
				if (foundMatch == null || foundMatch.length() < matchValue.length()) {
					foundMatch = matchValue;
					foundTokenType = tokenType;
				}
			}
		}
		if (foundMatch != null && foundTokenType != Token.Type.NONE) {
			token = Token.create(position, foundTokenType);
			token.appendString(foundMatch);
			position.next(foundMatch.length());
			position.move(foundMatch.length(), 0);
		}
		return token;
	}

	public void read() {
		Token token = null;
		while(position.getIndex() < buffer.length()) {
			char value = buffer.charAt(position.getIndex());
			// is whitespace delimiter?
			if (WHITESPACE.indexOf(value) >= 0) {
				pushToken(token);
				token = null;
				// is tab char?
				if (TAB_CHAR == value) {
					position.move(TAB_LENGTH, 0);
				}
				else {
					position.move(1, 0);
				}
				position.next();
				continue;
			}
			// is new line?
			if (NEW_LINE.indexOf(value) >= 0) {
				pushToken(token);
				token = null;
				position.next();
				position.move(0, 1);
				position.setColumn(0);
				continue;
			}
			// find a token in the seperator list
			Token nextToken = matchTokenSeperator(position.getIndex());
			if (nextToken != null) {
				pushToken(token);
				token = nextToken;
				switch (token.getType()) {
					case COMMENT:
						readUntil(token, NEW_LINE);
						pushToken(token);
						break;
					case STRING_OPEN_CLOSE:
						readStringToken(token);
						break;
					default:
						pushToken(token);
						break;
				}
				token = null;
				continue;
			}

			if (token == null) {
				token = Token.create(position, Token.Type.VALUE);
			}
			token.appendChar(value);
			position.next();
			position.move(1, 0);
		}
		pushToken(token);
	}

	public List<Token> getTokenList() {
		return tokenList;
	}

	public void printOut() {
		ListIterator<Token> tokenIterator = tokenList.listIterator();
		while (tokenIterator.hasNext()) {
			Token token = tokenIterator.next();
			TokenPosition position = token.getPosition();
			System.out.println(token.getType() + " " + position.getColumn() + "," + position.getRow() + " " +token.toString());
		}
	}

}
