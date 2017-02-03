package edu.isnap.eval.predict;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.ctd.hint.HintHighlighter;
import edu.isnap.ctd.util.NodeAlignment;
import edu.isnap.ctd.util.NodeAlignment.DistanceMeasure;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.hint.util.Spreadsheet;

public class ProgressAttributes implements AttributeGenerator {

	private final HashMap<String, Node> hintMap = new HashMap<>();
	private DistanceMeasure distanceMeasure;

	@Override
	public String getName() {
		return "progress";
	}

	@Override
	public void init(Map<AssignmentAttempt, Node> attemptMap, HintConfig config) {

		List<Node> allSubmitted = new LinkedList<>();
		for (AssignmentAttempt attempt : attemptMap.keySet()) {
			if (attempt.grade.average() == 1) {
				allSubmitted.add(SnapGradePrediction.getSubmittedNode(attempt));
			}
		}

		distanceMeasure = HintHighlighter.getDistanceMeasure(config);

		int i = 0;
		for (AssignmentAttempt attempt : attemptMap.keySet()) {
			List<Node> otherSubmitted = new LinkedList<>(allSubmitted);
			if (attempt.grade.average() == 1) {
				otherSubmitted.remove(i++);
			}

			Node currentCode = attemptMap.get(attempt);
			Node match = NodeAlignment.findBestMatch(
					currentCode, otherSubmitted, distanceMeasure);
			hintMap.put(attempt.id, match);
		}
	}

	@Override
	public void addAttributes(Spreadsheet spreadsheet, AssignmentAttempt attempt, Node node) {
		Node match = hintMap.get(attempt.id);
		double progress = -new NodeAlignment(node, match).calculateCost(
				distanceMeasure);
		double maxProgress = -new NodeAlignment(node, node).calculateCost(
				distanceMeasure);
		spreadsheet.put("progress", progress);
		spreadsheet.put("pProgress", progress / maxProgress);
	}

}
