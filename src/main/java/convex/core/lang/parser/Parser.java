package convex.core.lang.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class Parser {

	protected List<Token> tokenList;
	protected Node rootNode;
	protected Node topNode;

	private Parser(List<Token> tokenList) {
		this.tokenList = tokenList;
		this.rootNode = Node.create(Node.Type.ROOT);
	}


	public static Parser create(List<Token> tokenList) {
		return new Parser(tokenList);
	}

	public static Error createError(String message, Node node) {
		StringBuffer buffer = new StringBuffer(message);
		Token token = node.getToken();
		if (token != null) {
			TokenPosition position = token.getPosition();
			buffer.append(" at Line " + position.getRow() + 1 + ", Column " + position.getColumn() + 1);
		}
		return new Error(buffer.toString());
	}

	public void parse() {
		Node currentNode = rootNode;
		Node node;
		ListIterator<Token> tokenIterator = tokenList.listIterator();
		while (tokenIterator.hasNext()) {
			Token token = tokenIterator.next();
			switch(token.getType()) {
				case LIST_OPEN:
					node = Node.create(Node.Type.LIST, currentNode, token);
					currentNode = node;
					break;
				case LIST_CLOSE:
					if (currentNode.isType(Node.Type.LIST)) {
						// got the list back
					}
					else {
						throw Parser.createError("missing closing list bracket ')'", currentNode);
					}
					currentNode = currentNode.getParentNode();
					break;
				case VECTOR_OPEN:
					node = Node.create(Node.Type.VECTOR, currentNode, token);
					currentNode = node;
					break;
				case VECTOR_CLOSE:
					if (currentNode.isType(Node.Type.VECTOR)) {
						// got the list back
					}
					else {
						throw Parser.createError("missing closing vector bracket ']'", currentNode);
					}
					currentNode = currentNode.getParentNode();
					break;
			}
		}
	}

}
