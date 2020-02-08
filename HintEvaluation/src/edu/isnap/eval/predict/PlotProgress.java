package edu.isnap.eval.predict;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.csc200.Fall2016;
import edu.isnap.hint.ConfigurableAssignment;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.sourcecheck.HintHighlighter;
import edu.isnap.sourcecheck.NodeAlignment;
import edu.isnap.sourcecheck.NodeAlignment.DistanceMeasure;
import edu.isnap.util.Spreadsheet;

public class PlotProgress {
	public static void main(String[] args) {
		Assignment assignment = Fall2016.Squiral;
		Spreadsheet spreadsheet = new Spreadsheet();

		HintConfig config = ConfigurableAssignment.getConfig(assignment);
		DistanceMeasure distanceMeasure = HintHighlighter.getDistanceMeasure(config);

		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, true, true,
				new SnapParser.SubmittedOnly());

		List<Node> submissions = new LinkedList<>();
		for (AssignmentAttempt attempt : attempts.values()) {
			if (attempt.researcherGrade.average() < 1) continue;
			submissions.add(SimpleNodeBuilder.toTree(attempt.submittedSnapshot, true));
		}

		int timeSegment = 60;

		int i = 0;
		for (AssignmentAttempt attempt : attempts.values()) {
			double grade = attempt.researcherGrade.average();
			double partialGrade = attempt.researcherGrade.partialAverage();
			List<Node> otherSubmissions = new LinkedList<>(submissions);
			if (grade == 1) {
				otherSubmissions.remove(i++);
			}

			System.out.print(attempt.id + ": ");
			int nextTime = 0;
			for (AttemptAction action : attempt.rows) {
				if (action.currentActiveTime >= nextTime) {
					while (nextTime <= action.currentActiveTime) nextTime += timeSegment;

					Node currentCode = SimpleNodeBuilder.toTree(action.snapshot, true);
					Node match = NodeAlignment.findBestMatch(
							currentCode, otherSubmissions, distanceMeasure, config).to;
					double progress = -new NodeAlignment(currentCode, match, config)
							.calculateMapping(distanceMeasure).cost();
					double maxProgress = -new NodeAlignment(currentCode, currentCode, config)
							.calculateMapping(distanceMeasure).cost();

					spreadsheet.newRow();
					spreadsheet.put("attempt", attempt.id);
					spreadsheet.put("time", action.currentActiveTime);
					spreadsheet.put("rowID", action.id);
					spreadsheet.put("pTime", action.currentActiveTime / attempt.totalActiveTime);
					spreadsheet.put("grade", grade);
					spreadsheet.put("gradePC", partialGrade);
					spreadsheet.put("progress", progress);
					spreadsheet.put("pProgress", progress / maxProgress);

					System.out.print("|");
				}
			}
			System.out.println();
		}
		try {
			spreadsheet.write(assignment.analysisDir() + "/progress.csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
