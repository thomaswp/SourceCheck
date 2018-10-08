package pqgram.edits;

import java.util.ArrayList;
import java.util.Collection;

import astrecognition.model.Graph;
import edu.isnap.ctd.graph.Node;

public class Insertion extends PositionalEdit {

	private static String INSERTION_STRING = "%d: Insert %s on to %s (%d, %d) {%s}";

	private int end;
	private Collection<String> inheritedChildren;

	public Insertion(String a, String b, Graph aG, Graph bG, int start, int end) {
		super(a, b, aG, bG, start);
		this.end = end;
		this.inheritedChildren = new ArrayList<>();
	}

	public int getStart() {
		return super.getPosition();
	}

	public int getEnd() {
		return this.end;
	}

	public void addInheritedChild(String inheritedChild) {
		this.inheritedChildren.add(inheritedChild);
	}

	@Override
	public String toString() {
		String inheritedChildrenList = "";
		for (String inheritedChild : this.inheritedChildren) {
			inheritedChildrenList += inheritedChild + ", ";
		}
		return String.format(INSERTION_STRING, this.lineNumber, this.bG.getUniqueLabel(), this.aG.getUniqueLabel(), this.start, this.end, inheritedChildrenList);
	}

	@Override
	public Node outcome(Node from) {
		Node parent = aG.tag, inserted = bG.tag;
		if (parent == null || parent.root() != from) {
			// This can happen when an insertion has a missing parent.
			// We can safely ignore those insertions.
			return null;
		}
		Node node = parent.copy();
		int index = Math.min(this.start, node.children.size());
		node.children.add(index, inserted.constructNode(node, inserted.type()));
		return node.root();
	}
}
