package com.snap.eval.export;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONObject;

import com.snap.data.Snapshot;
import com.snap.parser.Assignment;
import com.snap.parser.Assignment.Dataset;
import com.snap.parser.AssignmentAttempt;
import com.snap.parser.AttemptAction;
import com.snap.parser.Store.Mode;

public class Datashop {

	private final static String[] HEADER = {
			"Anon Student Id",
			"Session Id",
			"Time",
			"Student Response Type",
			"Level (Type)",
			"Problem Name",
			"Selection",
			"Action",
			"Feedback Text",
			"Feedback Classification",
			"CF (AST)"
	};

	public static void main(String[] args) {
		export(Assignment.Fall2015.instance);
	}

	public static void export(Dataset dataset) {
		try {
			File output = new File(dataset.dataDir + "/analysis/datashop.txt");
			output.getParentFile().mkdirs();
			CSVPrinter printer = new CSVPrinter(new PrintWriter(output),
					CSVFormat.TDF.withHeader(HEADER));

			for (Assignment assignment : dataset.all()) {
				export(assignment, printer);
			}

			printer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void export(Assignment assignment, CSVPrinter printer) throws IOException {
		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
		for (AssignmentAttempt attempt : attempts.values()) {
			export(assignment, attempt, printer);
			break;
		}
	}

	private static void export(Assignment assignment, AssignmentAttempt attempt, CSVPrinter printer)
			throws IOException {
		String attemptID = attempt.id;
		String levelType = assignment.name.contains("HW") ? "HOMEWORK" : "IN-LAB";
		String problemName = assignment.name;

		String lastCode = null;

		for (AttemptAction action : attempt) {
			String message = action.message;

			String studentResponse = "ATTEMPT";
			String selection = "";
			String feedbackText = "";
			String feedbackClassification = "";
			String code = toCode(action.snapshot);

			if (AttemptAction.SHOW_HINT_MESSAGES.contains(message)) {
				studentResponse = "HINT_REQUEST";
				feedbackText = action.data;
				feedbackClassification = message;
				if (code != null) {
					System.err.printf("Hint with code (%s): %d\n", attemptID, action.id);
				}
				code = "";
			} else if (code != null && !code.equals(lastCode)) {
				lastCode = code;
				if (action.data != null && action.data.startsWith("{")) {
					JSONObject data = new JSONObject(action.data);
					if (data.has("id") && data.get("id") instanceof JSONObject) {
						data = data.getJSONObject("id");
					}
					if (data.has("selector") && data.has("id")) {
						selection = data.get("id") + "," + data.get("selector");
					}
				}
			} else {
				continue;
			}

			printer.printRecord(new Object[] {
					attemptID,
					action.sessionID,
					action.timestamp.getTime(),
					studentResponse,
					levelType,
					problemName,
					selection,
					message,
					feedbackText,
					feedbackClassification,
					code,
			});
		}
	}

	private static String toCode(Snapshot snapshot) {
		if (snapshot == null) return null;
		return snapshot.toCode(true).replace("\t", " ").replace("\n", " ");
	}
}
