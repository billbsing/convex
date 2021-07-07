package convex.core.lang.parser;


import java.lang.StringBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Token {


    public enum Type {
		NONE,
		VALUE,
		COMMENT,
		COMMENT_INLINE,
		STRING,
		LIST_OPEN,
		LIST_CLOSE,
		VECTOR_OPEN,
		VECTOR_CLOSE,
		SET_OPEN,
		META_OPEN,
		MAP_OPEN,
		MAP_META_SET_CLOSE,
		QUOTE,
		QUASI_QUOTE,
		UNQUOTE,
		UNQUOTE_SPLICING,

	};

	protected TokenPosition position;
	protected StringBuffer token;
	protected Type tokenType;



	private Token(TokenPosition position, Type tokenType) {
		this.position = TokenPosition.create(position);
		this.tokenType = tokenType;
		token = new StringBuffer();
	}

	public static Token create(TokenPosition position, Type tokenType) {
		return new Token(position, tokenType);
	}

	public void appendChar(char value) {
		token.append(value);
	}

	public void appendString(String value) {
		token.append(value);
	}

	public String toString() {
		return token.toString();
	}

	public int size() {
		return token.length();
	}

	public TokenPosition getPosition() {
		return position;
	}
	public Type getType() {
		return tokenType;
	}

	public boolean isType(Type value) {
        return tokenType == value;
    }

	public String getValue() {
		return token.toString();
	}

	public boolean isFirstChar(char value) {
		return token.charAt(0) == value;
	}

	public char charAt(int index) {
		return token.charAt(index);
	}

	public String subString(int position) {
		return token.substring(position);
	}

	public boolean isMatch(Pattern pattern) {
		Matcher m = pattern.matcher(token);
		return m.matches();
	}

}
