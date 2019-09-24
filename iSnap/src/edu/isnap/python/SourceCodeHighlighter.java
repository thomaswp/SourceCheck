package edu.isnap.python;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.isnap.hint.HintData;
import edu.isnap.hint.util.NullStream;
import edu.isnap.node.ASTNode;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.Deletion;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.EditSorter;
import edu.isnap.sourcecheck.edit.Insertion;
import edu.isnap.util.Diff;

public class SourceCodeHighlighter {

	/*public static String EDIT_START = "\u001b[31m"; //TODO: configure this for HTML or ASCII output
	public static String EDIT_END = "\u001b[0m";*/
	public static String DELETE_START = "<span class=\"deletion\">"; //TODO: configure this for HTML or ASCII output
	public static String DELETE_END = "</span>";
	public static String INSERT_START = "<span class=\"insertion\">";
	public static String INSERT_END = "</span>";

	// TODO: Eventually this should be a non-static method and the class
	// should allow configuration of the HTML output (e.g. colors, etc.)


	private static List<EditHint> sortEdits(List<EditHint> unsortedEdits){
		List<EditHint> sortedEdits = new ArrayList<EditHint>();
		sortedEdits.addAll(unsortedEdits);
		Collections.sort(sortedEdits, new EditSorter());
		return sortedEdits;
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
		System.out.println();

		if(edits.size() > 1) {
			System.out.println("Multiple edits suggested\n");
			//sort the edits by their location, so that last edit gets processed first. 
			edits = sortEdits(edits); //TODO: Need to consider overlapping areas.
		}

		String marked = studentCode.source;
		for (EditHint hint : edits) {
			ASTNode toDelete = null;
			ASTNode toInsert = null;
			if (hint instanceof Deletion) {
				Deletion del = (Deletion) hint;
				toDelete = (ASTNode) del.node.tag;
			}
			if (hint instanceof Insertion) {
				Insertion ins = (Insertion) hint;
				if (ins.replaced != null) toDelete = (ASTNode) ins.replaced.tag;
				if (ins.candidate != null) toInsert = (ASTNode) ins.candidate.tag; //It should only ever be one or the other?
			}
			if ( (toDelete == null || toDelete.hasType("null")) && (toInsert == null || toInsert.hasType("null")) ) {continue;}
			System.out.println("Hint:\n" + hint);

			if(toDelete != null) {
				System.out.println("Deletion: " + toDelete);
				System.out.println("Deletion location: " + toDelete.getSourceLocationStart() + " --> " + toDelete.getSourceLocationEnd());
				System.out.println("MARKED: ");
				if (toDelete.getSourceLocationEnd() == null) {
					marked += DELETE_END;
				} else {
					marked = toDelete.getSourceLocationEnd().markSource(marked, DELETE_END);
				}
				if (toDelete.getSourceLocationStart() != null) {
					marked = toDelete.getSourceLocationStart().markSource(marked, DELETE_START);
				} else {
					System.out.println("Missing source start: " + toDelete);
				}
			}
			if(toInsert != null) {
				System.out.println("Insertion: " + toInsert);
				System.out.println("Insertion location: " + toInsert.getSourceLocationStart() + " --> " + toInsert.getSourceLocationEnd());
				System.out.println("MARKED: ");

				//TODO: Need to think about how to handle other modifications throwing off the location index
				if(toInsert.getSourceLocationStart() != null) {
					marked = toInsert.getSourceLocationStart().markSource(marked, INSERT_START + toInsert.value + INSERT_END);
				}
			}
			
			System.out.println(marked + "\n");
		}
		return marked;

//		System.out.println(Diff.diff(from, to));
//		System.out.println(String.join("\n",
//				edits.stream().map(e -> e.toString()).collect(Collectors.toList())));
//		System.out.println("------------------------");
//		System.out.println();
	}
}
