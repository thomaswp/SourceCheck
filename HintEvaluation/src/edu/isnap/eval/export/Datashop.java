package edu.isnap.eval.export;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.ctd.util.map.DoubleMap;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Spring2016;
import edu.isnap.eval.user.CheckHintUsage;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.BlockDefinition;
import edu.isnap.parser.elements.CallBlock;
import edu.isnap.parser.elements.Code;
import edu.isnap.parser.elements.Code.Accumulator;
import edu.isnap.parser.elements.LiteralBlock;
import edu.isnap.parser.elements.LiteralBlock.Type;
import edu.isnap.parser.elements.Script;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.parser.elements.VarBlock;
import edu.isnap.parser.elements.util.Canonicalization;
import edu.isnap.parser.elements.util.IHasID;

public class Datashop {

	private final static String[] HEADER = {
			"Anon Student Id",
			"Session Id",
			"Time",
			"Student Response Type",
			"Level (Type)",
			"Problem Name",
			"Step Name",
			"Selection",
			"Action",
			"Input",
			"Feedback Text",
			"Feedback Classification",
			"CF (AST)"
	};

	public static void main(String[] args) throws IOException {
		export(Spring2016.instance);
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

	@SuppressWarnings("unused")
	private static void export(Assignment assignment) throws IOException {
		try {
			File output = new File(assignment.analysisDir() + "/datashop.txt");
			output.getParentFile().mkdirs();
			CSVPrinter printer = new CSVPrinter(new PrintWriter(output),
					CSVFormat.TDF.withHeader(HEADER));
			export(assignment, printer);
			printer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void export(Assignment assignment, CSVPrinter printer) throws IOException {
		System.out.println("---- Exporting: " + assignment + " ----");

		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false);
		for (AssignmentAttempt attempt : attempts.values()) {
			if (attempt.submittedActionID == AssignmentAttempt.NOT_SUBMITTED) continue;
			System.out.println(attempt.id);
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
				if (code.equals(lastCode)) {
					code = null;
				} else {
					lastCode = code;
					if (code.length() > 64000) {
						System.err.println("Long code: " + code.length());
					}
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
				int scriptIndex = -1;
				while (parent.tag instanceof Script || !(parent.tag instanceof IHasID)) {
					if (parent.tag instanceof Script) {
						scriptIndex = parent.index();
					} else {
						System.out.println("No ID: " + parent.type());
					}
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
				if (scriptIndex >= 0) {
					// Because scripts have no IDs, we use their parents' IDs, and mark which script
					// was referenced
					saveData.put("scriptIndex", scriptIndex);
				}
				saveData.put("from", fromArray);
				saveData.put("to", toArray);
				if (jsonData.has("message")) {
					saveData.put("message", jsonData.get("message"));
				}

				feedbackText = saveData.toString();
			}

			String stepTarget = selection.replaceAll("[,0-9]", "");
			String stepName = message + (stepTarget.length() > 0 ? "_" : "") + stepTarget;

			printer.printRecord(new Object[] {
					attemptID,
					action.sessionID,
					action.timestamp.getTime(),
					studentResponse,
					levelType,
					problemName,
					stepName,
					selection,
					message,
					message,
					feedbackText,
					feedbackClassification,
					code,
			});
		}
	}

	private static class Anonymizer {
		private DoubleMap<String, String, Integer> map = new DoubleMap<>();
		int count = 0;

		public void linkIDs(String type, String fromID, String toID) {
			map.put(type, fromID, getID(type, toID));
		}

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

		final JSONObject object = new OJSONObject();
		String type = code.type();
		object.put("type", type);
		if (code instanceof IHasID && !(code instanceof Script)) {
			String id = ((IHasID) code).getID();
			if (id != null) {
				if (code instanceof BlockDefinition) {
					// If this is a custom block definition, we link its name to it's GUID (if it has
					// one), so that customBlockRefs can reference the ID by it's name
					String name = ((BlockDefinition) code).name;
					anon.linkIDs(type, name, id);
				}
				object.put("id", anon.getID(type, id));
			}
		}

		// Special fields for certain nodes
		if (code instanceof VarBlock) {
			object.put("varRef", anon.getID("varRef", ((VarBlock) code).name));
		} else if (code instanceof LiteralBlock) {
			LiteralBlock literal = (LiteralBlock) code;
			if (literal.type == Type.VarMenu) {
				object.put("varRef", anon.getID("varRef", literal.value));
			} else {
				if (literal.type != Type.Text) {
					object.put("value", literal.value);
				} else {
					try {
						int value = Integer.parseInt(literal.value);
						object.put("value", value);
					} catch (NumberFormatException e) { }
				}
			}
		} else if (code instanceof CallBlock) {
			if (((CallBlock) code).isCustom) {
				object.put("blockType", "evaluateCustomBlock");
				object.put("customBlockRef", anon.getID("customBlock", code.value()));
			} else {
				object.put("blockType", code.value());
			}
		}

		JSONArray children = new JSONArray();
		JSONArray variableIDs = new JSONArray();

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
			public void add(String type, String value) {
				if (type.equals("var") || type.equals("varDec") || type.equals("varMenu")) {
					variableIDs.put(anon.getID("varRef", value));
				} else {
					JSONObject obj = new JSONObject();
					obj.put("type", type);
					children.put(obj);
				}
			}

			@Override
			public void add(Canonicalization canon) { }
		});

		if (variableIDs.length() > 0) {
			object.put("variableIDs", variableIDs);
		}

		if (children.length() > 0) {
			object.put("children", children);
		}

		return object;
	}

	// We want the fields to come out in the order we add them (with extendable children last)
	// for readability
	private static class OJSONObject extends JSONObject {
		public OJSONObject() {
			try {
				Field f = JSONObject.class.getDeclaredField("map");
			    f.setAccessible(true);
			    f.set(this, new LinkedHashMap<String, Object>());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
