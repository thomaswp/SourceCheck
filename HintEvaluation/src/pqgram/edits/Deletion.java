package pqgram.edits;

import java.util.Map;

import com.snap.graph.data.Node;

import astrecognition.model.Graph;
import astrecognition.model.Tree;

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
	public Node outcome(Map<String, Tree> map) {
		Node copy = map.get(b).tag.copy(false);
		copy.parent.children.remove(copy.index());
		return copy.root();
	}
}
