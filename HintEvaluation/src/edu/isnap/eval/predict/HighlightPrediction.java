package edu.isnap.eval.predict;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintHighlighter.EditHint;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.hint.util.Spreadsheet;

public class HighlightPrediction extends SnapGradePrediction {

	private final HashMap<String, List<EditHint>> hintMap = new HashMap<>();

	@Override
	protected String getName() {
		return "highlight";
	}

	@Override
	protected void init(Map<AssignmentAttempt, Node> attemptMap, HintConfig config) {

		List<Node> allSubmitted = new LinkedList<>(attemptMap.values());

		int i = 0;
		for (AssignmentAttempt attempt : attemptMap.keySet()) {
			Node toEval = attemptMap.get(attempt);

			List<Node> otherSubmitted = new LinkedList<>(allSubmitted);
			otherSubmitted.remove(i);

			HintHighlighter highlighter = new HintHighlighter(otherSubmitted, config);

			List<EditHint> hints = highlighter.highlight(toEval);
			hintMap.put(attempt.id, hints);

			i++;
		}
	}

	@Override
	protected void addAttributes(Spreadsheet spreadsheet, AssignmentAttempt attempt) {
		List<EditHint> hints = hintMap.get(attempt.id);
		int deletions = 0, insertions = 0, reoders = 0;
		int candidates = 0, replacements = 0;
		for (EditHint hint : hints) {
			if (hint instanceof HintHighlighter.Deletion) {
				deletions++;
			} else if (hint instanceof HintHighlighter.Insertion) {
				insertions++;
				if (((HintHighlighter.Insertion) hint).candidate != null) candidates++;
				if (((HintHighlighter.Insertion) hint).replacement != null) replacements++;

			} else if (hint instanceof HintHighlighter.Reorder) {
				reoders++;
			}
		}
		spreadsheet.put("deletions", deletions);
		spreadsheet.put("insertions", insertions);
		spreadsheet.put("reorders", reoders);
		spreadsheet.put("candidates", candidates);
		spreadsheet.put("replacement", replacements);
		spreadsheet.put("hints", hints.size());
	}

}
