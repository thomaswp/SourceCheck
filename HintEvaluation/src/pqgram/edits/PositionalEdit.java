package pqgram.edits;

import java.util.Map;

import com.snap.graph.data.Node;

import astrecognition.model.Graph;
import astrecognition.model.Tree;

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
	public Node outcome(Map<String, Tree> map) {
		throw new UnsupportedOperationException();
	}
}
