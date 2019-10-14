package edu.isnap.python;

import java.util.Optional;

import org.json.JSONObject;

import edu.isnap.hint.util.ASTNodeConverter;
import edu.isnap.node.ASTNode.SourceLocation;
import edu.isnap.node.ASTSnapshot;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;

public abstract class TextualNode extends Node {

	public Optional<Boolean> correct = Optional.empty();
	protected String source;

	public SourceLocation startSourceLocation;
	public SourceLocation endSourceLocation;

	@SuppressWarnings("unused")
	private TextualNode() {
		this(null, null, null, null);
	}

	public TextualNode(Node parent, String type, String value, String id) {
		super(parent, type, value, id);
	}

	public ASTSnapshot toASTSnapshot() {
		ASTSnapshot snapshot = super.toASTSnapshot(correct.orElse(false), source);
		snapshot.startSourceLocation = startSourceLocation;
		snapshot.endSourceLocation = endSourceLocation;
		return snapshot;
	}

	@Override
	protected Node shallowCopy(Node parent, Mapping mapping) {
		TextualNode copy = (TextualNode) super.shallowCopy(parent, mapping);
		copy.source = source;
		copy.correct = correct;
		copy.startSourceLocation = startSourceLocation == null ? null :
			startSourceLocation.copy();
		copy.endSourceLocation = endSourceLocation == null ? null :
			endSourceLocation.copy();
		return copy;
	}

	public String getSource() {
		if (parent == null) return source;
		String rootSource = ((TextualNode) root()).source;
		if (startSourceLocation == null && endSourceLocation == null) return null;
		return rootSource.substring(
				toIndex(rootSource, startSourceLocation),
				toIndex(rootSource, endSourceLocation));
	}

	private static int toIndex(String source, SourceLocation location) {
		String[] lines = source.split("\n");
		int index = 0;
		for (int i = 0; i < location.line - 1; i++) {
			index += lines[i].length() + 1;
		}
		index += location.col;
		return index;
	}

	public static TextualNode fromJSON(JSONObject jsonAST, String source,
			NodeConstructor constructor) {
		ASTSnapshot astNode = ASTSnapshot.parse(jsonAST, source);
		TextualNode node = (TextualNode) ASTNodeConverter.toNode(astNode, constructor);
		node.source = source;
		if (jsonAST.has("correct")) {
			boolean correct = jsonAST.getBoolean("correct");
			node.correct = Optional.of(correct);
		}
		return node;
	}
}