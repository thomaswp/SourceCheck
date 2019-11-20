package edu.isnap.node;

import java.util.Optional;

import org.json.JSONObject;

import edu.isnap.node.ASTNode.SourceLocation;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;

public abstract class TextualNode extends Node {

	public Optional<Boolean> correct = Optional.empty();
	private String source;

	// TODO: Protect
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

	@Override
	public void readFromASTNode(ASTNode node) {
		startSourceLocation = node.startSourceLocation;
		endSourceLocation = node.endSourceLocation;
	}

	/**
	 * Get the {@link SourceLocation} of the given insertion index in this nodes children.
	 * This may be the location of an existing child at that index (if present) or a calculated
	 * position of a hypothetical child to be added (e.g. at the end of the children).
	 */
	public SourceLocation getLocationOfChildIndex(int index) {
		// The Insertion's index is the index at which we want to insert in the parent
		if (index < children.size()) {
			// Otherwise, return the location of the first child at that location w/ a location
			SourceLocation location = null;
			while (location == null && index >= 0) {
				location = ((TextualNode) children.get(index)).startSourceLocation;
				index--;
			}
			return location;
		} else if (index == children.size()) {
			// If the index to insert is after all the other children...
			if (children.size() > 0) {
				// If there's a child to insert after, insert it *afterwards*
				TextualNode sibling = (TextualNode) children.get(index - 1);
				// System.out.println("Sibling: " + sibling);
				return sibling.endSourceLocation;
			} else {
				// Otherwise insert it after the first ancestor with an end source location
				TextualNode p = this;
				while (p.endSourceLocation == null) {
					p = (TextualNode) p.parent;
				}
				// System.out.println(p);
				return p.endSourceLocation;
			}
		}
		return null;
	}

	public static TextualNode fromJSON(JSONObject jsonAST, String source,
			NodeConstructor constructor) {
		ASTSnapshot astNode = ASTSnapshot.parse(jsonAST, source);
		TextualNode node = (TextualNode) fromASTNode(astNode, constructor);
		node.source = source;
		if (jsonAST.has("correct")) {
			boolean correct = jsonAST.getBoolean("correct");
			node.correct = Optional.of(correct);
		}
		return node;
	}
}