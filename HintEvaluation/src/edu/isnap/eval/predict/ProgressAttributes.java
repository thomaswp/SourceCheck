package edu.isnap.eval.predict;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.node.Node;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment;
import edu.isnap.sourcecheck.NodeAlignment.DistanceMeasure;
import edu.isnap.util.Spreadsheet;

public class ProgressAttributes implements AttributeGenerator {

	private final HashMap<String, Node> matches = new HashMap<>();
	private final HashMap<String, Double> grades = new HashMap<>();
	private DistanceMeasure distanceMeasure;

	@Override
	public String getName() {
		return "progress";
	}

	@Override
	public void init(Map<AssignmentAttempt, Node> attemptMap, HintConfig config) {

		List<Node> goodSubmitted = new LinkedList<>();
		List<Node> allSubmitted = new LinkedList<>();
		Map<Node, Double> submittedGrades = new HashMap<>();
		for (AssignmentAttempt attempt : attemptMap.keySet()) {
			Node submitted = SnapGradePrediction.getSubmittedNode(attempt);
			double grade = attempt.researcherGrade.average();
			if (grade == 1) {
				goodSubmitted.add(submitted);
			}
			allSubmitted.add(submitted);
			submittedGrades.put(submitted, grade);
		}

		distanceMeasure = HintHighlighter.getDistanceMeasure(config);

		int i = 0, j = 0;
		for (AssignmentAttempt attempt : attemptMap.keySet()) {
			List<Node> otherGoodSubmitted = new LinkedList<>(goodSubmitted);
			if (attempt.researcherGrade.average() == 1) {
				otherGoodSubmitted.remove(i++);
			}
			List<Node> otherSubmitted = new LinkedList<>(allSubmitted);
			otherSubmitted.remove(j++);

			Node currentCode = attemptMap.get(attempt);
			Node goodMatch = NodeAlignment.findBestMatch(
					currentCode, otherGoodSubmitted, distanceMeasure, new SnapHintConfig()).to;
			matches.put(attempt.id, goodMatch);

			Node closestMatch = NodeAlignment.findBestMatch(
					currentCode, otherSubmitted, distanceMeasure, new SnapHintConfig()).to;
			grades.put(attempt.id, submittedGrades.get(closestMatch));
		}
	}

	@Override
	public void addAttributes(Spreadsheet spreadsheet, AssignmentAttempt attempt, Node node) {
		Node match = matches.get(attempt.id);
		double progress = -new NodeAlignment(node, match, new SnapHintConfig()).calculateMapping(
				distanceMeasure).cost();
		double maxProgress = -new NodeAlignment(node, node, new SnapHintConfig()).calculateMapping(
				distanceMeasure).cost();
		spreadsheet.put("closestGrade", grades.get(attempt.id));
		spreadsheet.put("progress", progress);
		spreadsheet.put("pProgress", progress / maxProgress);
	}

}
