package edu.isnap.python;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringEscapeUtils;

import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.TextHint;
import edu.isnap.hint.util.NullStream;
import edu.isnap.node.ASTNode.SourceLocation;
import edu.isnap.node.Node.Annotations;
import edu.isnap.node.Node;
import edu.isnap.node.TextualNode;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment;
import edu.isnap.sourcecheck.NodeAlignment.DistanceMeasure;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.Insertion;
import edu.isnap.sourcecheck.edit.Suggestion;
import edu.isnap.sourcecheck.edit.Suggestion.SuggestionType;
import edu.isnap.util.Diff;

public class SourceCodeHighlighter {

	/*public static String EDIT_START = "\u001b[31m"; //TODO: configure this for HTML or ASCII output
	public static String EDIT_END = "\u001b[0m";*/
	public static String DELETE_START = "<span class=\"deletion\" "
			+ "data-tooltip=\"This code may be incorrect.\">";
	public static String INSERT_START = "<span class=\"insertion\"";
	public static String REPLACE_START = "<span class=\"replacement\" "
			+ "data-tooltip=\"This code may need to be replaced with something else.\">";
	public static String CANDIDATE_START = "<span class=\"candidate\" "
			+ "data-tooltip=\"This code is good, but it may be in the wrong place.\">";
//	public static String REORDER_START = "<span class=\"reorder\" "
//			+ "data-tooltip=\"This code is good, but it may be in the wrong place.\">";
	public static String SPAN_END = "</span>";
	
	public static String DELETE_ANNOTATION = "<span class=\"deletion\" ";
	public static String REPLACE_ANNOTATION = "<span class=\"replacement\" ";
	public static String CANDIDATE_ANNOTATION = "<span class=\"candidate\" ";

	/**
	 * @param hintData made of correct traces
	 * @param studentCode node of current student's code for which we show hints
	 * 
	 * @return
	 */
	public static String highlightSourceCode(HintData hintData, TextualNode studentCode) {
		HintHighlighter highlighter = hintData.hintHighlighter();

		highlighter.trace = NullStream.instance;

		List<EditHint> edits = highlighter.highlightWithPriorities(studentCode);
		Node copy = studentCode.copy();
		EditHint.applyEdits(copy, edits);

		Mapping mapping = highlighter.findSolutionMapping(studentCode);
		TextualNode target = (TextualNode) mapping.to;

		String marked = studentCode.getSource();

		List<Suggestion> suggestions = getSuggestions(edits);
		List<String> missing = new ArrayList<>();
		
		Mapping referenceMapping = null;
		if (hintData.config.useAnnotation) {
			int clusterID = 0;
			boolean v2 = hintData.config.sourceCheckV2;
			DistanceMeasure distanceMeasure = highlighter.getDistanceMeasure();
			
			if (target.cluster.isPresent()) {
				clusterID = target.cluster.get();
			}
			TextualNode reference = (TextualNode) hintData.getReferenceSolutions().get(clusterID);
			NodeAlignment align = new NodeAlignment(target, reference, hintData.config);
			referenceMapping = v2 ? align.align(distanceMeasure) : align.calculateMapping(distanceMeasure);
		}

		for (Suggestion suggestion : suggestions) {
			SourceLocation location = suggestion.location;
			EditHint hint = suggestion.hint; 
			if (!suggestion.start) {
				marked = location.markSource(marked, SPAN_END);
				continue;
			}
			if (referenceMapping != null) {
				TextualNode ref = null;
				switch (suggestion.type) {
				case DELETE: // If reorder or deletion, hint.node to get "from" in studentCode and hint.parent to get its parent. hint.parent is from EditHint 
					TextualNode parent = (TextualNode) hint.parent;
					ref = (TextualNode) referenceMapping.getFromMap().get(parent); // get corresponding parent node in reference
					break;
				case MOVE: // If insert, hint.candidate to get "from" in studentCode and hint.pair to get "to"
				case REPLACE: // If insert, hint.candidate to get "from" in studentCode and hint.pair to get "to"
				case INSERT:
					Insertion ins = (Insertion) hint; // hint.candidate to get "from" in studentCode. If null, it's insertion. hint.pair to get "to"
					TextualNode inserted = (TextualNode) ins.pair; // get node to be inserted
					ref = (TextualNode) referenceMapping.getFromMap().get(inserted); // get corresponding node in reference
					missing.add(getHumanReadableName((Insertion) hint, mapping.config));
				}
				String insertionCode;
				if (ref != null) {
					Annotations annotations = ref.readOnlyAnnotations();
					if (!annotations.equals(Annotations.EMPTY)) {
						List<TextHint> hints = annotations.getHints();
						insertionCode = getHTML(hints.get(0).text, suggestion.type);
					}else {
						insertionCode = getHTML(mapping, hint, suggestion.type);
					}
				}else {
					insertionCode = getHTML(mapping, hint, suggestion.type);
				}
				marked = location.markSource(marked, insertionCode);
			}else {
				switch (suggestion.type) {
				case DELETE:
					marked = location.markSource(marked, DELETE_START);
					break;
				case MOVE:
					marked = location.markSource(marked, CANDIDATE_START);
					break;
				case REPLACE:
					marked = location.markSource(marked, REPLACE_START);
					break;
				case INSERT:
					String insertionCode = getInsertHTML(mapping, hint);
					marked = location.markSource(marked, insertionCode);
					missing.add(getHumanReadableName((Insertion) hint, mapping.config));
				}
			}
			
			System.out.println(suggestion.type + ": " + suggestion.location);
//			System.out.println(marked);
		}

		if (!missing.isEmpty()) {
			marked += "\n\nYou may be missing the following:<ul>";
			for (String m : missing) marked += "<li/>" + m + "\n";
			marked += "</ul>";
		}

		//System.out.println(marked);
		return marked;
	}

	public static List<Suggestion> getSuggestions(List<EditHint> edits) {
		List<Suggestion> suggestions = new ArrayList<>();
		for (EditHint hint : edits) {
			hint.addSuggestions(suggestions);
		}
		Collections.sort(suggestions);
		return suggestions;
	}

	private static String getInsertHTML(Mapping mapping, EditHint editHint) {
		String hint = getInsertHint((Insertion)editHint, mapping.config);
		return getInsertHTML(hint);
	}
	
	private static String getInsertHTML(String hint) {
		String insertionCode = String.format("%s data-tooltip=\"%s\">%s%s",
				INSERT_START, hint, "\u2795", SPAN_END);
		return insertionCode;
	}
	
	private static String getHTML(Mapping mapping, EditHint editHint, SuggestionType type) {
		switch (type) {
		case DELETE: // If reorder or deletion, hint.node to get "from" in studentCode and hint.parent to get its parent. hint.parent is from EditHint 
			return DELETE_START;
		case MOVE: // If insert, hint.candidate to get "from" in studentCode and hint.pair to get "to"
			return CANDIDATE_START;
		case REPLACE: // If insert, hint.candidate to get "from" in studentCode and hint.pair to get "to"
			return REPLACE_START;
		case INSERT:
			String hint = getInsertHint((Insertion)editHint, mapping.config);
			return String.format("%s data-tooltip=\"%s\">%s%s", INSERT_START, hint, "\u2795", SPAN_END);
		}
		return null; 
	}
	
	private static String getHTML(String hint, SuggestionType type) {
		switch (type) {
		case DELETE: // If reorder or deletion, hint.node to get "from" in studentCode and hint.parent to get its parent. hint.parent is from EditHint 
			return String.format("%s data-tooltip=\"%s\">%s", REPLACE_ANNOTATION, hint, "\u2795");
		case MOVE: // If insert, hint.candidate to get "from" in studentCode and hint.pair to get "to"
			return String.format("%s data-tooltip=\"%s\">%s", CANDIDATE_ANNOTATION, hint, "\u2795");
		case REPLACE: // If insert, hint.candidate to get "from" in studentCode and hint.pair to get "to"
			return String.format("%s data-tooltip=\"%s\">%s", REPLACE_ANNOTATION, hint, "\u2795");
		case INSERT:
			return String.format("%s data-tooltip=\"%s\">%s%s", INSERT_START, hint, "\u2795", SPAN_END);
		}
		return null; 
	}

	private static String getInsertHint(Insertion insertion, HintConfig config) {
		String hrName = getHumanReadableName(insertion, config);
		String hint = "You may need to add " + hrName + " here";
		if (insertion.replaced != null) {
			hint += ", instead of what you have.";
		} else {
			hint += ".";
		}
		return StringEscapeUtils.escapeHtml(hint);
	}

	private static String getHumanReadableName(Insertion insertion, HintConfig config) {
		String hrName = config.getHumanReadableName(insertion.pair);
		if (insertion.replaced != null && insertion.replaced.hasType(insertion.type)) {
			hrName = hrName.replaceAll("^(an?)", "$1 different");
//			System.out.println(hrName);
		}
		return hrName;
	}


	@SuppressWarnings("unused")
	private static String getTextToInsert(Insertion insertion, Mapping mapping) {
		// TODO: Also need to handle newlines properly
		Node mappedPair = insertion.pair.applyMapping(mapping);
//		System.out.println("Pair:\n" + mappedPair);
		String source = ((TextualNode) mappedPair).getSource();
		if (source != null) return source;
		return insertion.pair.prettyPrint().replace("\n", "");
	}
}
