package pqgram.edits;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import astrecognition.model.Graph;
import astrecognition.model.Tree;
import edu.isnap.ctd.graph.Node;

public class Insertion extends PositionalEdit {
	
	private static String INSERTION_STRING = "%d: Insert %s on to %s (%d, %d) {%s}";
	
	private int end;
	private Collection<String> inheritedChildren;
	
	public Insertion(String a, String b, Graph aG, Graph bG, int start, int end) {
		super(a, b, aG, bG, start);
		this.end = end;
		this.inheritedChildren = new ArrayList<String>();
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
		return String.format(INSERTION_STRING, this.lineNumber, this.b, this.a, this.start, this.end, inheritedChildrenList);
	}
	
	@Override
	public Node outcome(Map<String, Tree> fromMap, Map<String, Tree> toMap) {
		if (!fromMap.containsKey(a)) return null;
		Node fromParent = fromMap.get(a).tag.copy(false);
		Node to = toMap.get(b).tag;
		Node toParent = to.parent;
		if (fromParent == null || toParent == null) return null;
		fromParent = fromParent.copy(false);
		
		// Walk through the both parents' children
		int toIndex = 0, fromIndex = 0;
		while (true) {
			if (fromIndex == fromParent.children.size()) {
				// If we reach the end of from's children, we must break
				break;
			}
			if (toParent.children.get(toIndex) == to) {
				// If we reach the node we want to insert, we break
				break;
			}
			if (toParent.children.get(toIndex).hasType(
					fromParent.children.get(fromIndex).type())) {
				// Otherwise, if the two nodes have the same label, increment both
				fromIndex++;
				toIndex++;
			} else {
				// Otherwise, skip one of the (presumably to be deleted) from children
				fromIndex++;
			}
		}
		
		
		Node insert = new Node(fromParent, to.type());
		fromParent.children.add(fromIndex, insert);
		return fromParent.root();
	}
}
