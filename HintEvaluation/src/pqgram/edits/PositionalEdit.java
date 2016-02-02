package pqgram.edits;

import com.snap.graph.data.Node;

import astrecognition.model.Graph;

/**
 * An edit which requires some positional description
 */
public class PositionalEdit extends Edit {
	protected int start;
	
	public PositionalEdit(String a, String b, Graph aG, Graph bG, int start) {
		super(a, b, aG, bG);
		this.start = start;
	}
	
	public int getPosition() {
		return this.start;
	}

	@Override
	public Node outcome() {
		throw new UnsupportedOperationException();
	}
}
