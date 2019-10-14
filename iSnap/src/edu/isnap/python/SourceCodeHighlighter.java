package edu.isnap.python;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang.StringEscapeUtils;

import edu.isnap.hint.HintConfig;
import edu.isnap.hint.HintData;
import edu.isnap.hint.util.NullStream;
import edu.isnap.node.ASTNode.SourceLocation;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.EditHint.EditType;
import edu.isnap.sourcecheck.edit.Insertion;
import edu.isnap.util.Diff;

public class SourceCodeHighlighter {

	/*public static String EDIT_START = "\u001b[31m"; //TODO: configure this for HTML or ASCII output
	public static String EDIT_END = "\u001b[0m";*/
	public static String DELETE_START = "<span class=\"deletion\" "
			+ "title=\"This code may be incorrect.\">";
	public static String INSERT_START = "<span class=\"insertion\"";
	public static String REPLACE_START = "<span class=\"replacement\" "
			+ "title=\"This code may need to be replaced with something else.\">";
	public static String CANDIDATE_START = "<span class=\"candidate\" "
			+ "title=\"This code is good, but it may be in the wrong place.\">";
	public static String REORDER_START = "<span class=\"reorder\" "
			+ "title=\"This code is good, but it may be in the wrong place.\">";
	public static String SPAN_END = "</span>";

	private static SortedMap<SourceLocation, EditHint> getSortedHintMap(List<EditHint> edits){
		SortedMap<SourceLocation, EditHint> editMap = new TreeMap<SourceLocation, EditHint>();
		for (EditHint hint : edits) {
			if(hint.getCorrectedEditStart() != null && hint.getCorrectedEditEnd() != null) {
				editMap.put(hint.getCorrectedEditStart(), hint);
				if (hint.getEditType() != EditType.INSERTION) {
					// Insertions don't have ends
					editMap.put(hint.getCorrectedEditEnd(), hint);
				}
			}
		}
		return editMap;
	}

	public static String highlightSourceCode(HintData hintData, TextualNode studentCode) {
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
		System.out.println(studentCode.source);
		System.out.println("Target");
		System.out.println(Diff.diff(studentCode.source, target.source, 2));
		System.out.println(from);
		mapping.printValueMappings(System.out);
		edits.forEach(System.out::println);
		System.out.println();
		String marked = studentCode.source;

		SortedMap<SourceLocation, EditHint> editMap = getSortedHintMap(edits);

		Set<String> missing = new LinkedHashSet<String>();
		for(Entry<SourceLocation, EditHint> editLocation : editMap.entrySet()) {
			EditHint editHint = editLocation.getValue();
			System.out.println("Location: " + editLocation.getKey() + "\nEditHint (" + editHint.getEditType()+ "):\n" + editLocation.getValue());
			SourceLocation location = editLocation.getKey();
			if(location == editHint.getCorrectedEditEnd() &&
					// Insertion handle both open and close spans
					editHint.getEditType() != EditType.INSERTION) {
				marked = location.markSource(marked, SPAN_END);
			} else if(location == editHint.getCorrectedEditStart()) {
				switch (editHint.getEditType()){
					case DELETION:
						marked = location.markSource(marked, DELETE_START);
						break;
					case REPLACEMENT:
						String insertionCode = getInsertHTML(mapping, editHint);
						marked = location.markSource(marked, insertionCode + REPLACE_START);
						missing.add(getHumanReadableName((Insertion) editHint, mapping.config));
						break;
					case INSERTION:
						insertionCode = getInsertHTML(mapping, editHint);
						marked = location.markSource(marked, insertionCode);
						missing.add(getHumanReadableName((Insertion) editHint, mapping.config));
						break;
					case CANDIDATE:
						marked = location.markSource(marked, CANDIDATE_START);
						break;
					case REORDER:
						marked = location.markSource(marked, REORDER_START);
						break;
				}
			}
//			System.out.println("MARKED: ");
//			System.out.println(marked + "\n");
		}

		if (!missing.isEmpty()) {
			marked += "\n\nYou may be missing the following:<ul>";
			for (String m : missing) marked += "<li/>" + m + "\n";
			marked += "</ul>";
		}

		return marked;
	}

	private static String getInsertHTML(Mapping mapping, EditHint editHint) {
		String hint = getInsertHint((Insertion)editHint, mapping.config);
		String insertionCode = String.format("%s title=\"%s\">%s%s",
				INSERT_START, hint, "<+>", SPAN_END);
		return insertionCode;
	}

	public static String getInsertHint(Insertion insertion, HintConfig config) {
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
		if (hrName == null) hrName = "some code";
		if (insertion.replaced != null && insertion.replaced.hasType(insertion.type)) {
			hrName = hrName.replaceAll("^(an?)", "$1 different");
			System.out.println(hrName);
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
