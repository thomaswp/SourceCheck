package edu.isnap.eval.user;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2015;
import edu.isnap.datasets.Fall2016;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.Store.Mode;

public class CreateStartTimesSpreadsheet {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		String formatBase = "http://arena.csc.ncsu.edu/%s/logging/view/display.php";
		createSpreadsheet(Fall2015.instance, String.format(formatBase, "history/fall2015"));
		createSpreadsheet(Fall2016.instance, String.format(formatBase, "snap"));
	}

	private static void createSpreadsheet(Dataset dataset, String baseURL) {
		Spreadsheet spreadsheet = new Spreadsheet();

		for (Assignment assignment : dataset.all()) {
			Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
			for (AssignmentAttempt attempt : attempts.values()) {
				if (!attempt.isSubmitted()) continue;

				int start = attempt.rows.getFirst().id;
				int end = attempt.submittedActionID;

				String link = String.format("%s?id=%s&start=%d&end=%d&snapshots=true", baseURL,
						attempt.id, start, end);

				spreadsheet.newRow();
				spreadsheet.put("dataset", dataset.getName());
				spreadsheet.put("assignment", assignment.name);
				spreadsheet.put("id", attempt.id);
				spreadsheet.put("link", link);
				spreadsheet.put("start", start);
				spreadsheet.put("code", "");
			}
		}

		try {
			spreadsheet.write(dataset.analysisDir() + "/time-template.csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
