package edu.isnap.python;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import edu.isnap.hint.HintData;
import edu.isnap.hint.util.NullStream;
import edu.isnap.node.ASTNode.SourceLocation;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.EditHint.EditType;
import edu.isnap.sourcecheck.edit.EditSorter;
import edu.isnap.sourcecheck.edit.Insertion;
import edu.isnap.util.Diff;

public class SourceCodeHighlighter {

	/*public static String EDIT_START = "\u001b[31m"; //TODO: configure this for HTML or ASCII output
	public static String EDIT_END = "\u001b[0m";*/
	public static String DELETE_START = "<span class=\"deletion\">"; //TODO: configure this for HTML or ASCII output
	public static String INSERT_START = "<span class=\"insertion\">";
	public static String REPLACE_START = "<span class=\"replacement\">";
	public static String CANDIDATE_START = "<span class=\"candidate\">";
	public static String REORDER_START = "<span class=\"reorder\">";
	public static String SPAN_END = "</span>";

	// TODO: Eventually this should be a non-static method and the class
	// should allow configuration of the HTML output (e.g. colors, etc.)


	private static List<EditHint> sortEdits(List<EditHint> unsortedEdits){
		List<EditHint> sortedEdits = new ArrayList<EditHint>();
		sortedEdits.addAll(unsortedEdits);
		Collections.sort(sortedEdits, new EditSorter());
		Collections.sort(sortedEdits, new EditSorter());
		return sortedEdits;
	}

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

	public static String highlightSourceCode(HintData hintData, PythonNode studentCode) {
		HintHighlighter highlighter = hintData.hintHighlighter();

		highlighter.trace = NullStream.instance;

		String from = studentCode.prettyPrint(true);
		List<EditHint> edits = highlighter.highlight(studentCode);
		Node copy = studentCode.copy();
		EditHint.applyEdits(copy, edits);

		Mapping mapping = highlighter.findSolutionMapping(studentCode);
		PythonNode target = (PythonNode) mapping.to;

		System.out.println(studentCode.id);
		System.out.println(studentCode.source);
		System.out.println("\nTarget (" + target.student + "):");
		System.out.println(Diff.diff(studentCode.source, target.source, 2));
		System.out.println(from);
		mapping.printValueMappings(System.out);
		edits.forEach(System.out::println);
		System.out.println();
		String marked = studentCode.source;

		SortedMap<SourceLocation, EditHint> editMap = getSortedHintMap(edits);

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
						String insertionCode =
							INSERT_START + ((Insertion)editHint).getTextToInsert() + SPAN_END;
						marked = location.markSource(marked, insertionCode + REPLACE_START);
						break;
					case INSERTION:
						insertionCode =
							INSERT_START + ((Insertion)editHint).getTextToInsert() + SPAN_END;
						marked = location.markSource(marked, insertionCode);
//						 + ((ASTNode)((Insertion)editHint).candidate.tag).value
						break;
					case CANDIDATE:
						marked = location.markSource(marked, CANDIDATE_START);
						break;
					case REORDER:
						marked = location.markSource(marked, REORDER_START);
						break;
				}
			}
			System.out.println("MARKED: ");
			System.out.println(marked + "\n");
		}
		return marked;
	}
}
