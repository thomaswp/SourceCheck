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

import com.snap.data.BlockDefinition;
import com.snap.data.CallBlock;
import com.snap.data.Canonicalization;
import com.snap.data.Code;
import com.snap.data.Code.Accumulator;
import com.snap.data.IHasID;
import com.snap.data.LiteralBlock;
import com.snap.data.Script;
import com.snap.data.Snapshot;
import com.snap.data.VarBlock;
import com.snap.eval.user.CheckHintUsage;
import com.snap.graph.SimpleNodeBuilder;
import com.snap.graph.data.Node;
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
		}
	}

	private static void export(Assignment assignment, AssignmentAttempt attempt, CSVPrinter printer)
			throws IOException {
		String attemptID = attempt.id;
		String levelType = assignment.name.contains("HW") ? "HOMEWORK" : "IN-LAB";
		String problemName = assignment.name;

		String lastCode = null;
		Snapshot lastSnapshot = null;

		Anonymizer anon = new Anonymizer();

		for (AttemptAction action : attempt) {
			String message = action.message;
			String data = action.data;

			String studentResponse = "ATTEMPT";
			String selection = "";
			String feedbackText = "";
			String feedbackClassification = "";
			String code = toCode(action.snapshot, anon);
			if (code != null) {
				lastSnapshot = action.snapshot;
				lastCode = code;
				if (code.length() > 64000) {
					System.err.println("Long code: " + code.length());
				}
			}

			if (action.data != null && data.startsWith("{")) {
				JSONObject jsonData = new JSONObject(data);
				if (jsonData.has("id") && jsonData.get("id") instanceof JSONObject) {
					jsonData = jsonData.getJSONObject("id");
				}
				if (jsonData.has("selector") && jsonData.has("id")) {
					String id = jsonData.get("id").toString();
					String selector = jsonData.get("selector").toString();
					id = String.valueOf(anon.getID(selector, id));
					selection = id + "," + jsonData.get("selector");
				}
			}
			if (data.startsWith("\"") && data.endsWith("\"")) {
				data = data.substring(1, data.length() - 1);
			}
			if (AttemptAction.SINGLE_ARG_MESSAGES.contains(message)) {
				selection = data;
			} else if (AttemptAction.SPRITE_ADD_VARIABLE.contains(message) ||
					AttemptAction.SPRITE_DELETE_VARIABLE.contains(message)) {
				selection = String.valueOf(anon.getID("varRef", data));
			} else if (AttemptAction.IDE_ADD_SPRITE.equals(message) ||
					AttemptAction.IDE_REMOVE_SPRITE.equals(message) ||
					AttemptAction.IDE_SELECT_SPRITE.equals(message)) {
				selection = String.valueOf(anon.getID("sprite", data));
			} else if (AttemptAction.HINT_DIALOG_LOG_FEEDBACK.equals(message)) {
				if (data.length() > 4) {
					selection = data.substring(2, data.length() - 2);
				}
			} else if (AttemptAction.SHOW_HINT_MESSAGES.contains(message)) {
				studentResponse = "HINT_REQUEST";
				feedbackClassification = message;

				JSONObject jsonData = new JSONObject(data);
				Node root = SimpleNodeBuilder.toTree(lastSnapshot, true);
				Node parent = CheckHintUsage.findParent(message, lastSnapshot, root, jsonData);
				if (parent == null) System.err.println("Null parent: " + data);
				while (!(parent.tag instanceof IHasID)) {
					System.out.println("No ID: " + parent.type());
					parent = parent.parent;
				}
				int parentID = anon.getID(parent.type(), ((IHasID)parent.tag).getID());
				selection = String.valueOf(parentID);

				JSONArray toArray = jsonData.getJSONArray("to");
				JSONArray fromArray;
				if (jsonData.has("from")) {
					fromArray = jsonData.getJSONArray("from");
				} else {
					fromArray = jsonData.getJSONArray("fromList").getJSONArray(0);
				}
				for (JSONArray array : new JSONArray[] {toArray, fromArray}) {
					if (array.length() > 0 && array.getString(0).equals("prototypeHatBlock")) {
						array.remove(0);
					}
				}

				JSONObject saveData = new JSONObject();
				saveData.put("parentID", parentID);
				saveData.put("parentType", parent.type());
				saveData.put("from", fromArray);
				saveData.put("to", toArray);

				feedbackText = saveData.toString();
			}

//			if (data.length() > 0 && selection.isEmpty()) {
//				System.out.println(message + ": " + data);
//			}

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
			if (code instanceof BlockDefinition) id = ((BlockDefinition) code).name;
			object.put("id", anon.getID(type, id));
		}

		if (code instanceof VarBlock) {
			object.put("varRef", anon.getID("varRef", ((VarBlock) code).name));
		} else if (code instanceof LiteralBlock && ((LiteralBlock) code).isVarRef) {
			object.put("varRef", anon.getID("varRef", ((LiteralBlock) code).value));
		} else if (code instanceof CallBlock && ((CallBlock) code).isCustom) {
			object.put("customBlockRef", anon.getID("customBlock", code.name(false)));
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
