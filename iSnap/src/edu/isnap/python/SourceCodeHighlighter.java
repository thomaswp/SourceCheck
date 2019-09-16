package edu.isnap.python;

import java.util.List;

import edu.isnap.hint.HintData;
import edu.isnap.hint.util.NullStream;
import edu.isnap.node.ASTNode;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.Deletion;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.edit.Insertion;
import edu.isnap.util.Diff;

public class SourceCodeHighlighter {

	/*public static String EDIT_START = "\u001b[31m"; //TODO: configure this for HTML or ASCII output
	public static String EDIT_END = "\u001b[0m";*/
	public static String DELETE_START = "<span class=\"delete\">"; //TODO: configure this for HTML or ASCII output
	public static String DELETE_END = "</span>";

	// TODO: Eventually this should be a non-static method and the class
	// should allow configuration of the HTML output (e.g. colors, etc.)
	
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

		for (EditHint hint : edits) {
			ASTNode toDelete = null;
			if (hint instanceof Deletion) {
				Deletion del = (Deletion) hint;
				toDelete = (ASTNode) del.node.tag;
			}
			if (hint instanceof Insertion) {
				Insertion ins = (Insertion) hint;
				if (ins.replaced != null) toDelete = (ASTNode) ins.replaced.tag;
			}
			if (toDelete == null || toDelete.hasType("null")) continue;
			System.out.println(hint);
			System.out.println(toDelete);
			System.out.println(toDelete.getSourceLocationStart() + " --> " +
					toDelete.getSourceLocationEnd());
			String marked = studentCode.source;
			if (toDelete.getSourceLocationEnd() == null) {
				marked += DELETE_END;
			} else {
				marked = toDelete.getSourceLocationEnd().markSource(marked, DELETE_END);
			}
			if (toDelete.getSourceLocationStart() != null) {
				marked = toDelete.getSourceLocationStart().markSource(marked, DELETE_START);
				System.out.println("MARKED: ");
			} else {
				System.out.println("Missing source start: " + toDelete);
			}
			System.out.println(marked);

			// TODO: Right now we return the code with only the first edit highighted
			// but we want to return the code with all edits highlighted
			return marked;
		}
		return studentCode.source;

//		System.out.println(Diff.diff(from, to));
//		System.out.println(String.join("\n",
//				edits.stream().map(e -> e.toString()).collect(Collectors.toList())));
//		System.out.println("------------------------");
//		System.out.println();
	}
}
