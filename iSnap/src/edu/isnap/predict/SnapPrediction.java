package edu.isnap.predict;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public abstract class SnapPrediction {

	protected abstract void init(List<AssignmentAttempt> attempts);
	protected abstract void addAttributes(Spreadsheet spreadsheet, AssignmentAttempt attempt);

	public void predict(Assignment... assignments) {

		List<AssignmentAttempt> attempts = new LinkedList<>();
		for (Assignment assignment : assignments) {
			attempts.addAll(assignment.load(Mode.Use, true, true, new SnapParser.SubmittedOnly())
					.values());
		}
		init(attempts);

		Spreadsheet spreadsheet = new Spreadsheet();
		for (AssignmentAttempt attempt : attempts) {
			spreadsheet.newRow();
//			spreadsheet.put("id", attempt.id);
//			spreadsheet.put("grade", attempt.grade.average());
//			spreadsheet.put("time", attempt.totalActiveTime);
			for (Entry<String, Integer> entry : attempt.grade.tests.entrySet()) {
				spreadsheet.put(entry.getKey().replace(" ", "_"), entry.getValue() == 2);
			}
			addAttributes(spreadsheet, attempt);
		}

		try {
			spreadsheet.write(assignments[0].analysisDir() + "/pred.csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
