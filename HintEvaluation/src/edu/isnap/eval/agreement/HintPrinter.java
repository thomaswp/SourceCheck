package edu.isnap.eval.agreement;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.csc200.Fall2016;
import edu.isnap.datasets.csc200.Spring2017;
import edu.isnap.eval.agreement.HintSelection.HintRequest;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.util.NullStream;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment.Mapping;
import edu.isnap.sourcecheck.edit.EditHint;
import edu.isnap.sourcecheck.priority.RuleSet;
import edu.isnap.util.Diff;
import edu.isnap.util.Diff.ColorStyle;

public class HintPrinter {

	public static void main(String[] args) throws FileNotFoundException {
		Diff.colorStyle = ColorStyle.None;
		RuleSet.trace = NullStream.instance;
		EditHint.useValues = false;
		Random rand = new Random(1234);
		printHints(Spring2017.Squiral, Fall2016.Squiral, rand);
		printHints(Spring2017.GuessingGame1, Fall2016.GuessingGame1, rand);
	}

	public static void printHints(Assignment hintRequestAssignment, Assignment trainingAssignment,
			Random rand) throws FileNotFoundException {

		StringBuilder sb = new StringBuilder();
		appendln(sb, "--------->> " + hintRequestAssignment.name + " <<---------");
		appendln(sb, "");

		List<HintRequest> selected = HintSelection.selectEarlyLate(
				hintRequestAssignment, new SnapParser.Filter[] {
					new SnapParser.SubmittedOnly()
				}, true, rand);

		HintHighlighter highlighter = new SnapHintBuilder(trainingAssignment)
				.buildGenerator(Mode.Ignore).hintHighlighter();
		highlighter.trace = NullStream.instance;

		for (HintRequest request : selected) {
			AttemptAction action = request.action;
			Node node = SimpleNodeBuilder.toTree(action.lastSnapshot, true);
			Mapping mapping = highlighter.findSolutionMapping(node);
			List<EditHint> hints = highlighter.highlight(node, mapping);


			String id = action.lastSnapshot.guid + " / " + action.id;

			appendln(sb, "---------- " +  id + " ----------");
			appendln(sb, "From:");
			appendln(sb, mapping.from.prettyPrintWithIDs());
			appendln(sb, "To:");
			appendln(sb, mapping.to.prettyPrintWithIDs());
			appendln(sb, "Target Solution ID: " + mapping.to.id);
			mapping.from.recurse(n -> {
				if (mapping.containsFrom(n) && mapping.getFrom(n).id != null) {
					appendln(sb, String.format("%s[%s -> %s]",
							n.type(), n.id, mapping.getFrom(n).id));
				}
			});
//			appendln(sb, mapping.itemizedCost());

			appendln(sb, String.join("\n",
					hints.stream()
					.map(e -> e.toString())
					.collect(Collectors.toList())));
			appendln(sb, "");
		}

		JsonAST.write(String.format("%s/%s.txt",
				hintRequestAssignment.analysisDir("print-hints"),
				trainingAssignment.dataset.getClass().getSimpleName()),
				sb.toString());
	}

	private static void appendln(StringBuilder sb, String out) {
		sb.append(out + "\n");
		System.out.println(out);
	}
}
