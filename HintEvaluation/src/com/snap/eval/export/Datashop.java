package com.snap.eval.export;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONArray;
import org.json.JSONObject;

import com.snap.data.Canonicalization;
import com.snap.data.Code;
import com.snap.data.Code.Accumulator;
import com.snap.data.IHasID;
import com.snap.data.LiteralBlock;
import com.snap.data.Script;
import com.snap.data.Snapshot;
import com.snap.data.VarBlock;
import com.snap.parser.Assignment;
import com.snap.parser.Assignment.Dataset;
import com.snap.parser.AssignmentAttempt;
import com.snap.parser.AttemptAction;
import com.snap.parser.Store.Mode;
import com.snap.util.DoubleMap;

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
		export(Assignment.Fall2016.instance);
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

		Anonymizer anon = new Anonymizer();

		for (AttemptAction action : attempt) {
			String message = action.message;

			String studentResponse = "ATTEMPT";
			String selection = "";
			String feedbackText = "";
			String feedbackClassification = "";
			String code = toCode(action.snapshot, anon);
			if (code != null) lastCode = code;

			if (AttemptAction.SHOW_HINT_MESSAGES.contains(message)) {
				studentResponse = "HINT_REQUEST";
				// TODO: Make this more accurate
				feedbackText = action.data;
				feedbackClassification = message;
				if (code != null) {
					System.err.printf("Hint with code (%s): %d\n", attemptID, action.id);
				}
				code = "";
			}

			if (action.data != null && action.data.startsWith("{")) {
				JSONObject data = new JSONObject(action.data);
				if (data.has("id") && data.get("id") instanceof JSONObject) {
					data = data.getJSONObject("id");
				}
				if (data.has("selector") && data.has("id")) {
					String id = data.get("id").toString();
					String selector = data.getString("selector").replace("evaluateCustomBlock", "doCusomtBlock");
					id = String.valueOf(anon.getID(selector, id));
					selection = id + "," + data.get("selector");
				}
			}
			if (AttemptAction.SINGLE_ARG_MESSAGES.contains(action.message)) {
				selection = action.data.substring(1, action.data.length() - 1);
			} else if (AttemptAction.SPRITE_ADD_VARIABLE.contains(action.message)) {
				selection = String.valueOf(anon.getID("varRef",
						action.data.substring(1, action.data.length() - 1)));
			}
			if (action.data.length() > 2 && selection.isEmpty()) {
				System.out.println(action.message + ": " + action.data);
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
					lastCode,
			});
		}
	}

	private static class Anonymizer {
		private DoubleMap<String, String, Integer> map = new DoubleMap<>();
		int count = 0;

		public int getID(String type, String id) {
			if (map.containsKey(type, id)) {
				return map.get(type, id);
			}
			map.put(type, id, count);
			return count++;
		}
	}

	private static String toCode(Snapshot snapshot, Anonymizer anon) {
		if (snapshot == null) return null;
		return toJSON(snapshot, anon).toString().replace("\t", " ");
	}

	private static JSONObject toJSON(Code code, Anonymizer anon) {
		if (code == null) return null;

		final JSONObject object = new JSONObject();
		String type = code.type();
		object.put("type", type);
		if (code instanceof IHasID && !(code instanceof Script)) {
			String id = ((IHasID) code).getID();
			object.put("id", anon.getID(type, id));
		}

		// TODO: Do something similar for custom block definitions and call
		if (code instanceof VarBlock) {
			object.put("varRef", anon.getID("varRef", ((VarBlock) code).name));
		} else if (code instanceof LiteralBlock && ((LiteralBlock) code).isVarRef) {
			object.put("varRef", anon.getID("varRef", ((LiteralBlock) code).value));
		}

		JSONArray children = new JSONArray();

		code.addChildren(false, new Accumulator() {

			@Override
			public void add(Iterable<? extends Code> codes) {
				for (Code code : codes) {
					add(code);
				}
			}

			@Override
			public void add(Code code) {
				children.put(toJSON(code, anon));
			}

			@Override
			public void addVariables(List<String> variables) {
				if (variables.size() == 0) return;
				JSONArray array = new JSONArray();
				for (String variable : variables) {
					array.put(anon.getID("varRef", variable));
				}
				object.put("variables", array);
			}

			@Override
			public void add(Canonicalization canon) { }
		});

		if (children.length() > 0) {
			object.put("children", children);
		}

		return object;
	}
}
