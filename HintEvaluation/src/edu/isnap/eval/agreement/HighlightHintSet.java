package edu.isnap.eval.agreement;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.hint.HintMap;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.Dataset;
import edu.isnap.eval.agreement.RateHints.HintOutcome;
import edu.isnap.eval.agreement.RateHints.HintRequest;
import edu.isnap.eval.agreement.RateHints.HintSet;
import edu.isnap.hint.SnapHintBuilder;
import edu.isnap.parser.Store.Mode;

public class HighlightHintSet extends HintSet {

	private final Dataset dataset;
	private final HintConfig config;

	public HighlightHintSet(String name, Dataset dataset, List<HintRequest> requests,
			HintConfig config) {
		super(name);
		this.dataset = dataset;
		this.config = config;
		addHints(requests);
	}

	private void addHints(List<HintRequest> requests) {
		Map<String, HintHighlighter> highlighters = new HashMap<>();

		Map<String, Assignment> assignmentMap = dataset.getAssignmentMap();
		HintMap baseMap = new HintMap(config);

		for (HintRequest request : requests) {
			HintHighlighter highlighter = highlighters.get(request.assignmentID);
			Assignment assignment = assignmentMap.get(request.assignmentID);
			if (highlighter == null) {
				SnapHintBuilder builder = new SnapHintBuilder(assignment, baseMap);
				highlighter = builder.buildGenerator(Mode.Ignore, 1).hintHighlighter();
				highlighters.put(request.assignmentID, highlighter);
			}

			Node code = request.code.copy();
			List<EditHint> hints = highlighter.highlightWithPriorities(code);

			for (EditHint hint : hints) {
				List<EditHint> edits = Collections.singletonList(hint);
				Node to = code.copy();
				EditHint.applyEdits(to, edits);
//				double priority = hint.priority.consensus();
//				if (priority < 0.35) continue;
				HintOutcome outcome = new HintOutcome(to, request.id, 1, edits);
				add(request.id, outcome);
			}
		}
	}

}
