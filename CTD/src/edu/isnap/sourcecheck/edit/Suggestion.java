package edu.isnap.sourcecheck.edit;

import java.util.List;

import edu.isnap.hint.util.Cast;
import edu.isnap.node.ASTNode.SourceLocation;
import edu.isnap.node.Node;
import edu.isnap.node.TextualNode;

public class Suggestion implements Comparable<Suggestion> {

	public enum SuggestionType {
		INSERT, DELETE, REPLACE, MOVE
	}

	public final EditHint hint;
	public final SourceLocation location;
	public final SuggestionType type;
	public final boolean start;

	public Suggestion(EditHint hint, SourceLocation location, SuggestionType type, boolean start) {
		this.hint = hint;
		this.location = location;
		this.type = type;
		this.start = start;
	}

	@Override
	public int compareTo(Suggestion s) {
		if (location == null) return 1;
		if (s == null) return -1;
		int comp = location.compareTo(s.location);
		// If the source locations are different, return the later one (we work backwards)
		if (comp != 0) return comp;
		// Otherwise, process starts before ends, so spans don't overlap
		if (start != s.start) return start ? 1 : -1;
		// Otherwise use the type order (reversed so the first is processed last)
		return -Integer.compare(type.ordinal(), s.type.ordinal());
	}

	public static void addSuggestionsForNode(List<Suggestion> suggestions, EditHint hint, Node node,
			SuggestionType type) {
		TextualNode textualNode = Cast.cast(node, TextualNode.class);
		if (textualNode == null) return;
		if (textualNode.startSourceLocation == null || textualNode.endSourceLocation == null) {
			return;
		}
		suggestions.add(new Suggestion(hint, textualNode.startSourceLocation, type, true));
		suggestions.add(new Suggestion(hint, textualNode.endSourceLocation, type, false));
	}
}