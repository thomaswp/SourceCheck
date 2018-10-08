package pqgram.edits;

import astrecognition.model.Graph;
import edu.isnap.ctd.graph.Node;

public class Deletion extends PositionalEdit {

	private static String DELETION_STRING = "%d: Delete %s from %s (%d)";

	public Deletion(String a, String b, Graph aG, Graph bG, int start) {
		super(a, b, aG, bG, start);
	}

	@Override
	public String toString() {
		return String.format(DELETION_STRING, this.lineNumber, this.bG.getUniqueLabel(), this.aG.getUniqueLabel(), this.start);
	}

	public Node parentNode() {
		return aG.tag;
	}

	public Node deletedNode() {
		return bG.tag;
	}

	@Override
	public Node outcome(Node from) {
		Node parent = parentNode(), deleted = deletedNode();
		if (deleted.root() != from) {
			throw new RuntimeException("Deleted node not in from");
		}
		if (deleted.parent != parent) {
			throw new RuntimeException("Deleted node not child of parent");
		}
		Node node = parent.copy();
		node.children.remove(deleted.index());
		return node.root();
	}
}
