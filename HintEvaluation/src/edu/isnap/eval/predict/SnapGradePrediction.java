package edu.isnap.eval.predict;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2015;
import edu.isnap.datasets.Fall2016;
import edu.isnap.hint.Configurable;
import edu.isnap.hint.HintConfig;
import edu.isnap.hint.SnapHintConfig;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.Node;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.util.Spreadsheet;

public class SnapGradePrediction {

	public static void main(String[] args) {
		new SnapGradePrediction(Fall2016.GuessingGame2, Fall2015.GuessingGame2)
			.predict(10, new RootPathAttributes(), new ProgressAttributes());
	}

	private final Assignment[] assignments;

	public SnapGradePrediction(Assignment... assignments) {
		this.assignments = assignments;
	}

	public static Node getSubmittedNode(AssignmentAttempt attempt) {
		return SimpleNodeBuilder.toTree(attempt.submittedSnapshot, true);
	}

	public void predict(AttributeGenerator... generators) {
		predict(-1, generators);
	}

	public void predict(int minute, AttributeGenerator... generators) {
		int second = minute * 60;

		Map<AssignmentAttempt, Node> attempts = new LinkedHashMap<>();
		for (Assignment assignment : assignments) {
			for (AssignmentAttempt attempt : assignment.load(Mode.Use, true, true,
					new SnapParser.SubmittedOnly()).values()) {
				// Here we can select another node, e.g. at a midpoint
				if (minute < 0) {
					attempts.put(attempt, getSubmittedNode(attempt));
				} else {
					for (AttemptAction action : attempt) {
						if (action.currentActiveTime >= second) {
							attempts.put(attempt, SimpleNodeBuilder.toTree(
									action.lastSnapshot, true));
							break;
						}
					}
				}
			}

		}
		HintConfig config = new SnapHintConfig();
		for (Assignment assignment : assignments) {
			if (assignment instanceof Configurable) {
				config = ((Configurable) assignment).getConfig();
			}
		}

		for (AttributeGenerator generator : generators) {
			generator.init(attempts, config);
		}

		Spreadsheet spreadsheet = new Spreadsheet();
		for (AssignmentAttempt attempt : attempts.keySet()) {
			spreadsheet.newRow();
			for (AttributeGenerator generator : generators) {
				generator.addAttributes(spreadsheet, attempt, attempts.get(attempt));
			}
//			spreadsheet.put("id", attempt.id);
//			spreadsheet.put("grade", attempt.grade.average());
			spreadsheet.put("gradePass", attempt.researcherGrade.average() >= 0.8);
//			spreadsheet.put("time", attempt.totalActiveTime);
//			for (Entry<String, Integer> entry : attempt.grade.tests.entrySet()) {
//				spreadsheet.put("Obj_" + entry.getKey().replace(" ", "_"), entry.getValue() == 2);
//			}
		}

		try {
			String m = minute < 0 ? "" : ("-" + minute);
			String g = "";
			for (AttributeGenerator gen : generators) g += "-" + gen.getName();
			spreadsheet.write(assignments[0].analysisDir() + "/pred" + g + m + ".csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
