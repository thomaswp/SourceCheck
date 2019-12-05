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

	public enum TagType {
		END, BOTH, START
	}

	public final EditHint hint;
	public final SourceLocation location;
	public final SuggestionType type;
	public final TagType tagType;

	public Suggestion(EditHint hint, SourceLocation location, SuggestionType type,
			TagType tagType) {
		this.hint = hint;
		this.location = location;
		this.type = type;
		this.tagType = tagType;
	}

	@Override
	public int compareTo(Suggestion s) {
		// -1 = process first (end-wards); 1 = process later (start-wards)
		if (location == null) return -1;
		if (s == null) return 1;
		// If the source locations are different, return the later one (we work backwards)
		int comp = -location.compareTo(s.location);
		if (comp != 0) return comp;
		// Otherwise, process starts before ends, so spans don't overlap
		comp = -Integer.compare(tagType.ordinal(), s.tagType.ordinal());
		if (comp != 0) return comp;
		// Otherwise use the type order (reversed so the first is processed last)
		comp = -Integer.compare(type.ordinal(), s.type.ordinal());
		if (comp != 0) return comp;
		// Otherwise, use the original order of the hints
		return -Integer.compare(hint.order, s.hint.order);
	}

	public static void addSuggestionsForNode(List<Suggestion> suggestions, EditHint hint, Node node,
			SuggestionType type) {
		TextualNode textualNode = Cast.cast(node, TextualNode.class);
		if (textualNode == null) return;
		if (textualNode.startSourceLocation == null || textualNode.endSourceLocation == null) {
			return;
		}
		suggestions.add(new Suggestion(hint, textualNode.startSourceLocation, type, TagType.START));
		suggestions.add(new Suggestion(hint, textualNode.endSourceLocation, type, TagType.END));
	}
}