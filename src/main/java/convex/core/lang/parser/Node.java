package convex.core.lang.parser;

import java.util.ArrayList;
import java.util.List;

import convex.core.data.ACell;


public class Node {

	protected Token token;
	protected List<Node> nodeList = new ArrayList<Node>();
	protected Node parentNode;
	protected ACell value;

	private Node(Node parentNode, Token token) {
		this.parentNode = parentNode;
		this.token = token;
		this.value = null;
	}

	public static Node create() {
		// create the root node
		return new Node(null, null);
	}

	public static Node create(Node parentNode, Token token) {
		return new Node(parentNode, token);
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

	public ACell[] getValues() {
		ACell result[] = new ACell[nodeList.size()];
		for (int index = 0; index < nodeList.size(); index ++) {
			result[index] = nodeList.get(index).getValue();
		}
		return result;
	}

	public ACell getValue() {
		return value;
	}

	public void setValue(ACell value) {
		this.value = value;
	}

	public Node getNode(int index) {
		return nodeList.get(index);
	}

	public List<Node> getNodeList() {
		return nodeList;
	}

}
