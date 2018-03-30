package edu.isnap.eval.milestones;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class SelectSquiralProjects {
	public static void main(String[] args) throws FileNotFoundException, IOException {
		writeRatingIDs();
	}

	public static void writeDataSpreadsheet() throws FileNotFoundException, IOException {
		String baseURL = "http://arena.csc.ncsu.edu/history/fall2016/logging/view/display.php";
		Spreadsheet spreadsheet = new Spreadsheet();
		Assignment assignment = Fall2016.Squiral;
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true,
				new SnapParser.SubmittedOnly());
		for (AssignmentAttempt attempt : attempts.values()) {
//			if (attempt.grade.average() != 1) continue;
			boolean usedHint = false;
			for (AttemptAction action : attempt) {
				if (AttemptAction.HINT_DIALOG_DESTROY.equals(action.message)) {
					usedHint = true;
					break;
				}
			}
			if (usedHint) continue;
			spreadsheet.newRow();
			spreadsheet.put("id", String.format(
					"=HYPERLINK(\"%s?id=%s&assignment=%s&start=%d&end=%d\", \"%s\")",
					baseURL, attempt.id, attempt.loggedAssignmentID, attempt.rows.getFirst().id,
					attempt.rows.getLast().id, attempt.id));
			spreadsheet.put("grade", attempt.grade.average());
			spreadsheet.put("activeTime", attempt.totalActiveTime);
		}

		spreadsheet.write(assignment.exportDir() + "/feature-tagging.csv");
	}

	public static void writeRatingIDs() {
		Assignment assignment = Fall2016.Squiral;
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true,
				new SnapParser.SubmittedOnly());
		for (AssignmentAttempt attempt : attempts.values()) {
//			if (attempt.grade.average() != 1) continue;
			boolean usedHint = false;
			for (AttemptAction action : attempt) {
				if (AttemptAction.HINT_DIALOG_DESTROY.equals(action.message)) {
					usedHint = true;
					break;
				}
			}
			if (usedHint) continue;
			System.out.println(attempt.id);
			for (AttemptAction action : attempt) {
				if (action.snapshot != null) {
					System.out.println(action.id);
				}
			}
			System.out.println("\n");
		}
	}
}
