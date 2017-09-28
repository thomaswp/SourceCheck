package edu.isnap.eval.agreement;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.RuleSet;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.Diff;
import edu.isnap.ctd.util.NodeAlignment.Mapping;
import edu.isnap.ctd.util.NullSream;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.export.JsonAST;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class HintPrinter {

	public static void main(String[] args) throws FileNotFoundException {
		Diff.USE_ANSI_COLORS = false;
		RuleSet.trace = NullSream.instance;
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

		List<AttemptAction> selected = HintSelection.select(
				hintRequestAssignment, new SnapParser.Filter[] {
					new SnapParser.SubmittedOnly()
				}, true, rand);

		HintHighlighter highlighter = new SnapHintBuilder(trainingAssignment)
				.buildGenerator(Mode.Ignore).hintHighlighter();
		highlighter.trace = NullSream.instance;

		for (AttemptAction action : selected) {
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
			List<Node> keys = new ArrayList<>(mapping.keysetFrom());
			Collections.sort(keys, (a, b) -> Integer.compare(a.depth(), b.depth()) * 2 +
					Integer.compare(a.index(), b.index()));
			keys.stream().filter(n -> n.id != null)
				.forEach(k -> appendln(sb,
						String.format("%s[%s -> %s]", k.type(), k.id, mapping.getFrom(k).id)));
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
