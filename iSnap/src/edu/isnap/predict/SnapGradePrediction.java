package edu.isnap.predict;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.hint.HintConfig;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.hint.Configurable;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public abstract class SnapGradePrediction {

	protected abstract void init(Map<AssignmentAttempt, Node> attemptMap, HintConfig config);
	protected abstract void addAttributes(Spreadsheet spreadsheet, AssignmentAttempt attempt);
	protected abstract String getName();

	public static Node getSubmittedNode(AssignmentAttempt attempt) {
		return SimpleNodeBuilder.toTree(attempt.submittedSnapshot, true);
	}

	public void predict(Assignment... assignments) {

		Map<AssignmentAttempt, Node> attempts = new LinkedHashMap<>();
		for (Assignment assignment : assignments) {
			for (AssignmentAttempt attempt : assignment.load(Mode.Use, true, true,
					new SnapParser.SubmittedOnly()).values()) {
				// Here we can select another node, e.g. at a midpoint
				attempts.put(attempt, getSubmittedNode(attempt));
			}

		}
		HintConfig config = new HintConfig();
		for (Assignment assignment : assignments) {
			if (assignment instanceof Configurable) {
				config = ((Configurable) assignment).getConfig();
			}
		}
		init(attempts, config);

		Spreadsheet spreadsheet = new Spreadsheet();
		for (AssignmentAttempt attempt : attempts.keySet()) {
			spreadsheet.newRow();
//			spreadsheet.put("id", attempt.id);
			spreadsheet.put("grade", attempt.grade.average());
			spreadsheet.put("gradePass", attempt.grade.average() >= 0.8);
//			spreadsheet.put("time", attempt.totalActiveTime);
			for (Entry<String, Integer> entry : attempt.grade.tests.entrySet()) {
				spreadsheet.put("Obj_" + entry.getKey().replace(" ", "_"), entry.getValue() == 2);
			}
			addAttributes(spreadsheet, attempt);
		}

		try {
			spreadsheet.write(assignments[0].analysisDir() + "/pred-" + getName() + ".csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
