package edu.isnap.python;

import java.util.Optional;

import edu.isnap.node.ASTSnapshot;
import edu.isnap.node.Node;

public abstract class TextualNode extends Node {

	public Optional<Boolean> correct = Optional.empty();
	public String student;
	public String source;

	@SuppressWarnings("unused")
	private TextualNode() {
		this(null, null, null, null);
	}

	public TextualNode(Node parent, String type, String value, String id) {
		super(parent, type, value, id);
	}

	public ASTSnapshot toASTSnapshot() {
		return super.toASTSnapshot(correct.orElse(false), source);
	}

	@Override
	public Node shallowCopy(Node parent) {
		TextualNode copy = (TextualNode) super.shallowCopy(parent);
		copy.source = this.source;
		copy.student =this.student;
		copy.correct = this.correct;
		return copy;

	}
}