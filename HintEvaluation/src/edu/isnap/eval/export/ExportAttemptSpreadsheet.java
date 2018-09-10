package edu.isnap.eval.export;

import java.io.IOException;
import java.util.Collection;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Fall2016;
import edu.isnap.datasets.Fall2017;
import edu.isnap.datasets.Spring2016;
import edu.isnap.datasets.Spring2017;
import edu.isnap.hint.util.Spreadsheet;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;

public class ExportAttemptSpreadsheet {

	public static void main(String[] args) throws IOException {
		Dataset[] datasets = new Dataset[] {
				Spring2016.instance, Fall2016.instance,
				Spring2017.instance, Fall2017.instance
		};
		for (Dataset dataset : datasets) {
			System.out.println("Exporting: " + dataset.getName());
			exportDataset(dataset);
		}
	}

	public static void exportDataset(Dataset dataset) throws IOException {
		Spreadsheet spreadsheet = new Spreadsheet();
		for (Assignment assignment : dataset.all()) {
			spreadsheet.setHeader("assignmentID", assignment.name);
			exportAssignment(assignment, spreadsheet);
		}
		spreadsheet.write(dataset.dataDir + "/export/submitted-links.csv");
	}

	public static void exportAssignment(Assignment assignment, Spreadsheet spreadsheet)
			throws IOException {
		String datasetFolder = assignment.dataset.getName();
		datasetFolder = datasetFolder.substring(0, 1).toLowerCase() + datasetFolder.substring(1);
		String baseURL = String.format(
				"http://arena.csc.ncsu.edu/history/%s/logging/view/display.php", datasetFolder);
		Collection<AssignmentAttempt> attempts = assignment.load(
				Mode.Use, false, true, new SnapParser.SubmittedOnly()).values();
		for (AssignmentAttempt attempt : attempts) {
			if (attempt.rows.size() == 0) continue;
			spreadsheet.newRow();
			spreadsheet.put("id", String.format(
					"=HYPERLINK(\"%s?id=%s&assignment=%s&start=%d&end=%d\", \"%s\")",
					baseURL, attempt.id, attempt.loggedAssignmentID, attempt.rows.getFirst().id,
					attempt.rows.getLast().id, attempt.id));
			spreadsheet.put("grade", attempt.grade == null ? "" : attempt.grade.average());
			spreadsheet.put("activeTime", attempt.totalActiveTime);
			spreadsheet.put("usedHints", gotHintDialog(attempt));
		}
	}

	public static void exportAssignment(Assignment assignment) throws IOException {
		Spreadsheet spreadsheet = new Spreadsheet();
		exportAssignment(assignment, spreadsheet);
		spreadsheet.write(assignment.exportDir() + "/submitted-links.csv");
	}

	public static boolean gotHintDialog(AssignmentAttempt attempt) {
		for (AttemptAction action : attempt) {
			if (AttemptAction.HINT_DIALOG_DESTROY.equals(action.message)) {
				return true;
			}
		}
		return false;
	}
}
