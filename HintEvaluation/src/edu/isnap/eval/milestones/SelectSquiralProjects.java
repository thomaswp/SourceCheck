package edu.isnap.eval.milestones;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.datasets.Fall2016;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class SelectSquiralProjects {

	public final static Assignment Assignment = Fall2016.Squiral;

	public static void main(String[] args) throws FileNotFoundException, IOException {
		writeDataSpreadsheet();
		writeRatingIDs();
	}

	public static void writeDataSpreadsheet() throws FileNotFoundException, IOException {
		String baseURL = "http://arena.csc.ncsu.edu/history/fall2016/logging/view/display.php";
		Spreadsheet spreadsheet = new Spreadsheet();
		List<AssignmentAttempt> attempts = selectAttempts();
		for (AssignmentAttempt attempt : attempts) {
			spreadsheet.newRow();
			spreadsheet.put("id", String.format(
					"=HYPERLINK(\"%s?id=%s&assignment=%s&start=%d&end=%d\", \"%s\")",
					baseURL, attempt.id, attempt.loggedAssignmentID, attempt.rows.getFirst().id,
					attempt.rows.getLast().id, attempt.id));
			spreadsheet.put("grade", attempt.grade.average());
			spreadsheet.put("activeTime", attempt.totalActiveTime);
		}

		spreadsheet.write(Assignment.exportDir() + "/feature-tagging.csv");
	}

	public static List<AssignmentAttempt> selectAttempts() {
		Map<String, AssignmentAttempt> attempts = Assignment.load(Mode.Use, false, true,
				new SnapParser.SubmittedOnly());
		List<AssignmentAttempt> selected = new ArrayList<>();
		for (AssignmentAttempt attempt : attempts.values()) {
			boolean usedHint = false;
			for (AttemptAction action : attempt) {
				if (AttemptAction.HINT_DIALOG_DESTROY.equals(action.message)) {
					usedHint = true;
					break;
				}
			}
			if (usedHint) continue;
			selected.add(attempt);
		}
		return selected;
	}

	public static void writeRatingIDs() {
		List<AssignmentAttempt> attempts = selectAttempts();
		for (AssignmentAttempt attempt : attempts) {
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
