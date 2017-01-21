package pqgram.edits;

import java.util.Map;

import astrecognition.model.Graph;
import astrecognition.model.Tree;
import edu.isnap.ctd.graph.Node;

public class Deletion extends PositionalEdit {

	private static String DELETION_STRING = "%d: Delete %s from %s (%d)";

	public Deletion(String a, String b, Graph aG, Graph bG, int start) {
		super(a, b, aG, bG, start);
	}

	@Override
	public String toString() {
		return String.format(DELETION_STRING, this.lineNumber, this.b, this.a, this.start);
	}

	@Override
	public Node outcome(Map<String, Tree> fromMap, Map<String, Tree> toMap) {
		if (!fromMap.containsKey(b)) return null;
		Node copy = fromMap.get(b).tag.copy();
		copy.parent.children.remove(copy.index());
		return copy.root();
	}
}
