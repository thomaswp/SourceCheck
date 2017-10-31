package edu.isnap.eval.agreement;

import java.util.List;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.edit.EditHint;
import edu.isnap.ctd.util.map.ListMap;
import edu.isnap.eval.agreement.TutorEdits.TutorEdit;

public class RateHints {

	public static void rate(GoldStandard standard, HintSet hintSet) {
		// TODO
	}

	public static class HintSet extends ListMap<Integer, HintOutcome> {
		public final String name;

		public HintSet(String name) {
			this.name = name;
		}
	}

	public static class HintOutcome {
		public final Node outcome;
		public final double weight;
		public final List<EditHint> edits;

		public HintOutcome(Node outcome, double weight, List<EditHint> edits) {
			this.outcome = outcome;
			this.weight = weight;
			this.edits = edits;
		}
	}

	public static class GoldStandard {

		public GoldStandard(ListMap<String, TutorEdit> consensusEdits) {
			// TODO
		}

	}
}
