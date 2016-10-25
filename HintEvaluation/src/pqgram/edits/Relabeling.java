package pqgram.edits;

import java.util.Map;

import astrecognition.model.Graph;
import astrecognition.model.Tree;
import edu.isnap.ctd.graph.Node;


public class Relabeling extends Edit {
	
	private static String RELABELING_STRING = "%d: Relabel %s to %s";
	
	public Relabeling(String a, String b, Graph aG, Graph bG) {
		super(a, b, aG, bG);
	}
	
	@Override
	public String toString() {
		return String.format(RELABELING_STRING, this.lineNumber, this.a, this.b);
	}
	
	public Node getParentNode(Map<String, Tree> fromMap) {
		if (!fromMap.containsKey(a)) return null;
		return fromMap.get(a).tag;
	}

	@Override
	public Node outcome(Map<String, Tree> fromMap, Map<String, Tree> toMap) {
		if (!fromMap.containsKey(a)) return null;
		Node copy = fromMap.get(a).tag.copy(false);
		copy.setType(bG.getLabel());
		return copy.root();
	}
}