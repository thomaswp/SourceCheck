package edu.isnap.eval.user;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2016;
import edu.isnap.eval.util.Spreadsheet;
import edu.isnap.parser.Store.Mode;

public class TimeEval {

	private final static int IDLE_DURATION = 60;
	private final static int SKIP_DURATION = 60 * 5;

	public static void main(String[] args) throws FileNotFoundException, IOException {
//		String formatBase = "http://arena.csc.ncsu.edu/%s/logging/view/display.php";
//		createSpreadsheet(Fall2015.instance, String.format(formatBase, "history/fall2015"));
//		createSpreadsheet(Fall2016.instance, String.format(formatBase, "snap"));

		eval(Fall2016.instance);
	}

	private static void eval(Dataset dataset) {

		Spreadsheet spreadsheet = new Spreadsheet();

		for (Assignment assignment : dataset.all()) {
			Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
			for (AssignmentAttempt attempt : attempts.values()) {
				if (!attempt.isSubmitted()) continue;

				int activeTime = 0;
				int idleTime = 0;
				int segments = 0;
				long lastTime = 0;

				for (AttemptAction action : attempt) {

					if (action.id > attempt.submittedActionID) {
						throw new RuntimeException("Attempt " + attempt.id +
								" has actions after submission.");
					}

					long time = action.timestamp.getTime() / 1000;

					if (lastTime == 0) {
						lastTime = time;
						segments++;
						continue;
					}

					int duration = (int) (time - lastTime);
					if (duration < SKIP_DURATION) {
						int idleDuration = Math.max(duration - IDLE_DURATION, 0);
						activeTime += duration - idleDuration;
						idleTime += idleDuration;
					} else {
						segments++;
					}

					lastTime = time;
				}

				spreadsheet.newRow();
				spreadsheet.put("dataset", dataset.getName());
				spreadsheet.put("assignment", assignment.name);
				spreadsheet.put("id", attempt.id);
				spreadsheet.put("active", activeTime);
				spreadsheet.put("idle", idleTime);
				spreadsheet.put("total", activeTime + idleTime);
				spreadsheet.put("segments", segments);
			}
		}

		try {
			spreadsheet.write(dataset.analysisDir() + "/time.csv");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unused")
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
