package edu.isnap.python;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONObject;

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
import edu.isnap.sourcecheck.edit.Suggestion.SuggestionType;
import edu.isnap.sourcecheck.edit.Suggestion.TagType;
import edu.isnap.util.Diff;

public class SourceCodeHighlighter {

	//TODO: configure this for HTML or ASCII output
	public static String INSERT_START = "<span class=\"insertion\"";
	public static String SPAN_END = "</span>";

	public static PrintStream out = NullStream.instance;
	static {
		// Hack to get logging on dev but not prod
		String os = System.getProperty("os.name");
		if (os != null && os.contains("Windows")) {
			out = System.out;
		}
	}

	public static class SourceCodeFeedbackHTML {
		private String highlightedCode;
		private boolean changed;
		private List<String> missingCode = new ArrayList<>();

		public final SourceCodeHighlightConfig config;

		public SourceCodeFeedbackHTML(SourceCodeHighlightConfig config) {
			this.config = config;
		}

		public String getMissingHTML() {
			String missingHTML = "";
			if (config.showMissing && !missingCode.isEmpty()) {
				missingHTML += "\n\n" + config.getMissingHeader() + "<ul>";
				for (String m : missingCode) { missingHTML += "<li>" + m + "</li>" ; }
				missingHTML += "</ul>";
			}
			return missingHTML;
		}

		public String getHeader() {
			return "We have added suggestions to your code below. Hover over one to see it. " +
					"<small>[<a href='http://go.ncsu.edu/cs108-hints' target='_blank'>"
					+ "What is this?</a>]</small>";
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
			if (!config.showHints) return null;
			if (!changed) return config.getNoSuggestionsText();
			return String.format("<div><p>%s</p>"
					+ "<pre class='display' style='padding: 3px;'>%s</pre>"
					+ "<p class='missing'>%s</p>"
					+ "</div>",
					getHeader(),
					highlightedCode,
					getMissingHTML()
			);
		}

		public JSONObject toJSON() {
			JSONObject jsonObj = new JSONObject();
			jsonObj.put("highlighted", getAllHTML());
			jsonObj.put("hasHints", changed);
			jsonObj.put("config", config.toJSON());
			return jsonObj;
		}
	}

	public enum HighlightLevel {
		DeleteOnly, DeleteReplaceMove, All
	}


	public static class SourceCodeHighlightConfig {
		/** True if any hints should be shown */
		public final boolean showHints;
		/** How much highlight information to show */
		public final HighlightLevel highlightLevel;
		/** Whether to suggest (true) or ask questions (false) suggestions */
		public final boolean suggest;
		/** Whether to show missing code summary at the end */
		public final boolean showMissing;
		/** Seed which was used to generate the hash code used to assign conditions */
		public final String conditionSeed;
		/** If true, showHints is reversed (e.g. for switching replications) */
		public final boolean reverseShow;

		public SourceCodeHighlightConfig(String conditionSeed, boolean reverseShow) {
			this.conditionSeed = conditionSeed;
			this.reverseShow = reverseShow;
			Random random = conditionSeed == null ? new Random() :
					new Random(conditionSeed.hashCode());
			this.showHints = random.nextBoolean() == reverseShow;
			HighlightLevel level;
			if (random.nextBoolean()) {
				level = HighlightLevel.All;
			} else {
				// For now, no delete-only hints
				level = HighlightLevel.DeleteReplaceMove;
			}
			this.highlightLevel = level;
			this.showMissing = random.nextBoolean();
			this.suggest = random.nextBoolean();
		}

		public String getNoSuggestionsText() {
			String response = "We have checked your code, and ";
			switch (highlightLevel) {
			case DeleteOnly:
			case DeleteReplaceMove:
				return response + "everything looks good so far (though you may not be done).";
			default:
				return response + "everything looks good!";
			}

		}

		public SourceCodeHighlightConfig(boolean showHints, HighlightLevel highlightLevel,
				boolean suggest, boolean showMissing) {
			this.showHints = showHints;
			this.highlightLevel = highlightLevel;
			this.showMissing = showMissing;
			this.suggest = suggest;
			this.conditionSeed = null;
			this.reverseShow = false;
		}

		public SourceCodeHighlightConfig() {
			this(true, HighlightLevel.All, true, true);
		}

		public JSONObject toJSON() {
			JSONObject obj = new JSONObject();
			obj.put("showHints", showHints);
			obj.put("highlightLevel", highlightLevel.toString());
			obj.put("highlightLevelInt", highlightLevel.ordinal());
			obj.put("suggest", suggest);
			obj.put("showMissing", showMissing);
			obj.put("conditionSeed", conditionSeed);
			obj.put("reversedShow", reverseShow);
			return obj;
		}

		public boolean shouldIgnore(SuggestionType type) {
			switch (highlightLevel) {
			case All: return false;
			case DeleteReplaceMove: return type == SuggestionType.INSERT;
			case DeleteOnly: return type != SuggestionType.DELETE;
			}
			return false;
		}

		public String getDeletionTip() {
			return suggest ? "This code may be incorrect." : "Is this code incorrect?";
		}

		public String getMoveTip() {
			return suggest ? "This code is good, but it may be in the wrong place." :
				"This code is good, but is it in the wrong place?";
		}

		public String getReplaceTip() {
			return suggest ? "This code may need to be replaced with something else." :
				"Should this code be replaced with something else?";
		}

		public String getMissingHeader() {
			return suggest ? "You may be missing the following:" :
				"Are you missing any of the following?:";
		}

		public String getInsertTip(String hrName, boolean replaced, boolean shouldAppearOnNewline) {
			String start = suggest ? "You may need to add " : "Do you need to add ";
			String end = suggest ? "." : "?";
			String hint = "";
			if(!replaced && shouldAppearOnNewline) {
				hint = start + hrName + " on another line";
			} else {
				hint = start + hrName + " here";
			}
			if (replaced) {
				hint += ", instead of what you have" + end;
			} else {
				hint += end;
			}
			return hint;
		}
	}


	private final SourceCodeHighlightConfig highlightConfig;

	public SourceCodeHighlighter(SourceCodeHighlightConfig highlightConfig) {
		this.highlightConfig = highlightConfig;
	}

	public SourceCodeHighlighter() {
		this(new SourceCodeHighlightConfig());
	}

	public SourceCodeFeedbackHTML highlightSourceCode(HintData hintData,
			TextualNode studentCode) {
		SourceCodeFeedbackHTML feedback = new SourceCodeFeedbackHTML(highlightConfig);
		if (!highlightConfig.showHints) return feedback;

		HintHighlighter highlighter = hintData.hintHighlighter();

		highlighter.trace = NullStream.instance;

		List<EditHint> edits = highlighter.highlight(studentCode);
		Node copy = studentCode.copy();
		EditHint.applyEdits(copy, edits);

		Mapping mapping = highlighter.findSolutionMapping(studentCode);
		TextualNode target = (TextualNode) mapping.to;

//		target.recurse(n -> out.println(((TextualNode) n).startSourceLocation));
		out.println(studentCode.id);
		out.println(studentCode.getSource());
		out.println("Target");
		out.println(Diff.diff(studentCode.getSource(), target.getSource(), 2));
		out.println(Diff.diff(studentCode.prettyPrint(), target.prettyPrint(), 2));
		mapping.printValueMappings(out);
		edits.forEach(out::println);
		out.println();
		String marked = studentCode.getSource();

		List<Suggestion> suggestions = getSuggestions(edits);
		if (suggestions.size() > 0) feedback.changed = true;

		for (Suggestion suggestion : suggestions) {
			SourceLocation location = suggestion.location;
			out.println(suggestion.type + ": " + suggestion.location);
			EditHint hint = suggestion.hint;
			if (suggestion.tagType == TagType.END) {
				marked = location.markSource(marked, SPAN_END);
				continue;
			}
			switch (suggestion.type) {
			case DELETE:
			case MOVE:
			case REPLACE:
				marked = location.markSource(marked, getSpanStart(suggestion.type));
				break;
			case INSERT:
				marked = location.markSource(marked, getInsertHTML(mapping, hint));
				break;
			}
//			out.println(marked);
		}
		marked = removeComments(marked);
		out.println(marked);

		if (highlightConfig.showMissing) {
			for (EditHint hint : edits) {
				if (hint instanceof Insertion) {
					feedback.missingCode.add(
							getHumanReadableName((Insertion) hint, mapping.config));
					feedback.changed = true;
				}
			}
		}

		feedback.highlightedCode = marked;

//		out.println(marked);
		return feedback;
	}

	private static String removeComments(String source) {
		List<String> lines = new ArrayList<>(Arrays.asList(source.split("\n")));
		boolean commenting = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			boolean remove = false;
			if (!commenting && line.trim().startsWith("\"\"\"")) {
				commenting = true;
				remove = true;
			} else if (line.contains("\"\"\"")) {
				commenting = false;
				remove = true;
			} else if (commenting) {
				remove = true;
			}
			if (line.contains("<span")) remove = false;
			if (remove) lines.remove(i--);
		}
		return String.join("\n", lines);
	}

	private List<Suggestion> getSuggestions(List<EditHint> edits) {
		List<Suggestion> suggestions = new ArrayList<>();
		for (EditHint hint : edits) {
			hint.addSuggestions(suggestions);
		}
		for (int i = 0; i < suggestions.size(); i++) {
			if (highlightConfig.shouldIgnore(suggestions.get(i).type)) {
				suggestions.remove(i--);
			}
		}
		Collections.sort(suggestions);
//		out.println("Sugg:");
//		suggestions.forEach(out::println);
		return suggestions;
	}

	private String getSpanStart(SuggestionType type) {
		String typeString = "";
		String tip = "";
		switch(type) {
		case DELETE:
			typeString = "deletion";
			tip = highlightConfig.getDeletionTip();
			break;
		case MOVE:
			typeString = "candidate";
			tip = highlightConfig.getMoveTip();
			break;
		case REPLACE:
			typeString = "replacement";
			tip = highlightConfig.getReplaceTip();
			break;
		default:
			System.err.print("Unknown type: " + type);
		}

		return String.format("<span class=\"%s\" data-tooltip=\"%s\">", typeString, tip);
	}

	private String getInsertHTML(Mapping mapping, EditHint editHint) {
		String hint = getInsertHint((Insertion)editHint, mapping.config);
		String insertionCode = String.format("%s data-tooltip=\"%s\">%s%s",
				INSERT_START, hint, "\u2795", SPAN_END);
		return insertionCode;
	}

	public String getInsertHint(Insertion insertion, HintConfig config) {
		String hrName = getHumanReadableName(insertion, config);
		String hint = highlightConfig.getInsertTip(hrName,
				insertion.replaced != null,
				config.shouldAppearOnNewline(insertion.pair));
		return StringEscapeUtils.escapeHtml(hint);
	}

	private static String getHumanReadableName(Insertion insertion, HintConfig config) {
		String hrName = config.getHumanReadableName(insertion.pair);
		if (insertion.replaced != null && insertion.replaced.hasType(insertion.type)) {
			hrName = hrName.replaceAll("^(an?)", "$1 different");
		}
		return hrName;
	}


	public static String getTextToInsert(Insertion insertion, Mapping mapping) {
		// TODO: Also need to handle newlines properly
		Node mappedPair = insertion.pair.applyMapping(mapping);
//		out.println("Pair:\n" + mappedPair);
		String source = ((TextualNode) mappedPair).getSource();
		if (source != null) return source;
		return insertion.pair.prettyPrint().replace("\n", "");
	}
}
