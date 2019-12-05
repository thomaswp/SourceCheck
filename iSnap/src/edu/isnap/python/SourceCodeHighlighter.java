package edu.isnap.python;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringEscapeUtils;

import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.util.NullStream;
import edu.isnap.node.ASTNode.SourceLocation;
import edu.isnap.node.Node;
import edu.isnap.node.TextualNode;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.Insertion;
import edu.isnap.sourcecheck.edit.Suggestion;
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

	public static class SourceCodeFeedbackHTML {
		public String highlightedCode;
		public List<String> missingCode = new ArrayList<>();

		public String getMissingHTML() {
			String missingHTML = "";
			if (!missingCode.isEmpty()) {
				missingHTML += "\n\nYou may be missing the following:<ul>";
				for (String m : missingCode) { missingHTML += "<li>" + m + "</li>" ; }
				missingHTML += "</ul>";
			}
			return missingHTML;
		}

		public String getHeader() {
			return "We have added suggestions to your code below. Hover over one to see it.";
		}

		/**
		 * Builds an HTML string from the annotated code with suggestions. For nicer display/easier
		 * integration into the PCRS system. The string will have a <div> block as the outermost
		 * element, with a <p>, <div>, and <p> as its children. The first paragraph is simply a
		 * display header. The highlighted code that is passed in will be split and used to make the
		 * second and third elements, which are the annotated student code and the suggestions for
		 * what to add, respectively.
		 */
		public String getAllHTML() {
			return "<div>" +
					"<p>" + getHeader() + "</p>" +
					"<pre class='display'>" + highlightedCode + "</pre>" +
					"<p class='missing'>" + getMissingHTML() + "</p>" +
//					"<iframe src=\"https://www.qualtrics.com\"></iframe>" +
				   "</div>";
		}
	}


	public static SourceCodeFeedbackHTML highlightSourceCode(HintData hintData,
			TextualNode studentCode) {
		HintHighlighter highlighter = hintData.hintHighlighter();

		highlighter.trace = NullStream.instance;

		String from = studentCode.prettyPrint(true);
		List<EditHint> edits = highlighter.highlight(studentCode);
		Node copy = studentCode.copy();
		EditHint.applyEdits(copy, edits);

		Mapping mapping = highlighter.findSolutionMapping(studentCode);
		TextualNode target = (TextualNode) mapping.to;

//		target.recurse(n -> System.out.println(((TextualNode) n).startSourceLocation));
		System.out.println(studentCode.id);
		System.out.println(studentCode.getSource());
		System.out.println("Target");
		System.out.println(Diff.diff(studentCode.getSource(), target.getSource(), 2));
		System.out.println(Diff.diff(from, target.prettyPrint(true), 2));
		mapping.printValueMappings(System.out);
		edits.forEach(System.out::println);
		System.out.println();
		String marked = studentCode.getSource();

		List<Suggestion> suggestions = getSuggestions(edits);
		List<String> missing = new ArrayList<>();

		for (Suggestion suggestion : suggestions) {
			SourceLocation location = suggestion.location;
			EditHint hint = suggestion.hint;
			if (!suggestion.start) {
				marked = location.markSource(marked, SPAN_END);
				continue;
			}
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
			System.out.println(suggestion.type + ": " + suggestion.location);
			System.out.println(marked);
		}

		SourceCodeFeedbackHTML feedback = new SourceCodeFeedbackHTML();
		feedback.highlightedCode = marked;
		feedback.missingCode.addAll(missing);

		System.out.println(marked);
		return feedback;
	}

	private static List<Suggestion> getSuggestions(List<EditHint> edits) {
		List<Suggestion> suggestions = new ArrayList<>();
		for (EditHint hint : edits) {
			hint.addSuggestions(suggestions);
		}
		Collections.sort(suggestions);
		return suggestions;
	}

	private static String getInsertHTML(Mapping mapping, EditHint editHint) {
		String hint = getInsertHint((Insertion)editHint, mapping.config);
		String insertionCode = String.format("%s data-tooltip=\"%s\">%s%s",
				INSERT_START, hint, "\u2795", SPAN_END);
		return insertionCode;
	}

	public static String getInsertHint(Insertion insertion, HintConfig config) {
		String hrName = getHumanReadableName(insertion, config);
		String hint = "";
		if(config.shouldAppearOnNewline(insertion.pair)) {
			hint = "You may need to add " + hrName + " on the next line";
		} else {
			hint = "You may need to add " + hrName + " here";
		}
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


	public static String getTextToInsert(Insertion insertion, Mapping mapping) {
		// TODO: Also need to handle newlines properly
		Node mappedPair = insertion.pair.applyMapping(mapping);
//		System.out.println("Pair:\n" + mappedPair);
		String source = ((TextualNode) mappedPair).getSource();
		if (source != null) return source;
		return insertion.pair.prettyPrint().replace("\n", "");
	}
}
