package pqgram.edits;

import com.snap.graph.data.Node;

import astrecognition.model.Graph;


public class Relabeling extends Edit {
	
	private static String RELABELING_STRING = "%d: Relabel %s to %s";
	
	public Relabeling(String a, String b, Graph aG, Graph bG) {
		super(a, b, aG, bG);
	}
	
	@Override
	public String toString() {
		return String.format(RELABELING_STRING, this.lineNumber, this.a, this.b);
	}

	@Override
	public Node outcome() {
		Node copy = aG.tag.copy(false);
		copy.setType(bG.getLabel());
		return copy.root();
	}
}