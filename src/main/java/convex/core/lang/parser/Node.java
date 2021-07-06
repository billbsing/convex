package convex.core.lang.parser;

import java.util.ArrayList;
import java.util.List;


public class Node {

	enum Type {
		ROOT,
		LIST,
		VECTOR,
		MAP,
		SET,
	};

	protected Token token;
	protected Type nodeType;
	protected List<Node> nodeList = new ArrayList<Node>();
	protected Node parentNode;

	private Node( Type nodeType, Node parentNode, Token token) {
		this.parentNode = parentNode;
		this.nodeType = nodeType;
		this.token = token;
	}

	public static Node create(Type nodeType) {
		return new Node(nodeType, null, null);
	}

	public static Node create(Type nodeType, Node parentNode) {
		return new Node(nodeType, parentNode, null);
	}

	public static Node create(Type nodeType,  Node parentNode, Token token) {
		return new Node(nodeType, parentNode, token);
	}

	public Token getToken() {
		return token;
	}

	public Node getParentNode() {
		return parentNode;
	}

	public void addNode(Node node) {
		nodeList.add(node);
	}
	public boolean isType(Type nodeType) {
		return nodeType == this.nodeType;
	}
}
