package edu.isnap.eval.grader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Spring2016;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.Store.Mode;

public class CreateGradingBaseSpreadsheet {
	public static void main(String[] args) throws FileNotFoundException, IOException {
		String formatBase = "http://arena.csc.ncsu.edu/%s/logging/view/display.php";
		createSpreadsheet(Spring2016.instance, String.format(formatBase, "history/spring2016"));
	}

	private static void createSpreadsheet(Dataset dataset, String baseURL) {

		for (Assignment assignment : dataset.all()) {
			Spreadsheet spreadsheet = new Spreadsheet();
			Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
			System.out.println(assignment.name);
			for (AssignmentAttempt attempt : attempts.values()) {
				if (!attempt.isSubmitted()) continue;

				int start = attempt.rows.getFirst().id;
				int end = attempt.submittedActionID;

				int lastSnapshotID = 0;
				for (AttemptAction action : attempt) {
					if (action.snapshot != null) lastSnapshotID = action.id;
					if (action.id >= end) break;
				}

				String link = String.format(
						"%s?id=%s&assignment=%s&start=%d#%d",
						baseURL, attempt.id, assignment.name, start, lastSnapshotID);
				link = String.format("=hyperlink(\"%s\", \"%s\")", link, attempt.id);

				spreadsheet.newRow();
				spreadsheet.put("Project ID", link);
				spreadsheet.put("Graded ID", end);

				System.out.println(link);

			}
			System.out.println();

			try {
				spreadsheet.write(assignment.analysisDir() + "/grading-base.csv");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
