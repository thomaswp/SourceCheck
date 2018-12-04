package edu.isnap.eval.export;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.ctd.graph.Node;
import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.user.CheckHintUsage;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.ASTNode;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.ListBlock;
import edu.isnap.parser.elements.Script;
import edu.isnap.parser.elements.Snapshot;
import edu.isnap.parser.elements.util.IHasID;
import edu.isnap.rating.RatingConfig;
import edu.isnap.util.Spreadsheet;
import edu.isnap.util.map.CountMap;

public class ProgSnap2Dataset implements Closeable {

//	private final static String[] MAIN_EVENT_TABLE_HEADER = {
//			"EventType",
//			"EventID",
//			"Order",
//			"SubjectID",
//			"Toolnstances",
//			"CodeStateID",
//			"ParentEventID",
//			"ClientTimestamp",
//			"ClientTimezone",
//			"SessionID",
//			"CourseID",
//			"TermID",
//			"AssignmentID",
//			"ExperimentalCondition",
//			"CodeStateSection",
//			"EventInitiator",
//			"EditType",
//			"InterventionType",
//			"InterventionMessage",
//	};
//
//	private final static String[] METADATA_HEADER = {
//			"Property",
//			"Value",
//	};
//
//	private final static String[] CODE_STATES_HEADER = {
//			"ID",
//			"Code",
//	};
//
//	private final static String[] SUBJECT_ASSIGNMENT_LINK_HEADER = {
//			"SubjectID",
//			"AssignmentID",
//			"Grade",
//			"NumberOfGraders",
//	};
//
//	private final static String[] EVENT_LINK_HEADER = {
//			"EventID",
//			"Relevance",
//			"Progress",
//			"Interpretability",
//	};

	private final String dir;
	private final Spreadsheet mainTable;
	private final Spreadsheet assignmentLinkTable;

	private final Set<String> unexportedMessages = new LinkedHashSet<>();
	private final CountMap<String> users = new CountMap<>();

	public PrintStream out = System.out;

	public static void main(String[] args) throws IOException {
		exportAndWrite(Spring2017.instance);
	}

	public static void exportAndWrite(Assignment assignment)
			throws FileNotFoundException, IOException {
		ProgSnap2Dataset progsnap = new ProgSnap2Dataset(assignment.dataDir);
		progsnap.export(assignment);
		progsnap.close();
	}

	public static void exportAndWrite(Dataset dataset)
			throws FileNotFoundException, IOException {
		ProgSnap2Dataset progsnap = new ProgSnap2Dataset(dataset.dataDir);
		progsnap.export(dataset);
		progsnap.close();
	}

	public ProgSnap2Dataset(String dir) throws IOException {
		this.dir = dir;
		JsonAST.values.clear();
		mainTable = new Spreadsheet(CSVFormat.RFC4180);
		mainTable.beginWrite(dir + "/ProgSnap2/MainTable.csv");

		assignmentLinkTable = new Spreadsheet(CSVFormat.RFC4180);
		assignmentLinkTable.beginWrite(dir + "/ProgSnap2/LinkTables/Assignment.csv");
	}

	@Override
	public void close() throws IOException {
		mainTable.endWrite();
		assignmentLinkTable.endWrite();

		try {
			JsonAST.write(dir + "/ProgSnap2-values.txt",
					String.join("\n", JsonAST.values));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		out.println("\nUnexported messages:");
		unexportedMessages.forEach(System.out::println);
		out.println("\nUsers:");
		users.entrySet().forEach(System.out::println);
	}

	public void export(Dataset dataset) throws IOException {
		for (Assignment assignment : dataset.all()) {
			export(assignment);
		}
	}

	public void export(Assignment assignment) throws IOException {
		out.println("---- Exporting: " + assignment + " ----");


		assignmentLinkTable.newRow();
		assignmentLinkTable.put("ID", assignment.name);
		assignmentLinkTable.put("URL", "file:///Resources/Assignments.pdf");
		assignmentLinkTable.put("Type", assignment.name.contains("HW") ? "Homework" : "In-Lab");

		mainTable.setHeader("AssignmentID", assignment.name);
		mainTable.setHeader("ToolInstances", assignment.dataset.getToolInstances());

		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true,
				new SnapParser.SubmittedOnly());
		for (AssignmentAttempt attempt : attempts.values()) {
			if (attempt.submittedActionID == AssignmentAttempt.NOT_SUBMITTED) continue;
			out.println(attempt.id);
			export(assignment, attempt, mainTable);
		}
	}

	private void export(Assignment assignment, AssignmentAttempt attempt, Spreadsheet spreadsheet)
			throws IOException {


		String userID = attempt.userID();
		if (userID == null || userID.isEmpty()) {
			userID = attempt.id;
			out.println("No UID: " + attempt.id);
		} else if (userID.length() > 16) {
			userID = userID.substring(userID.length() - 16, userID.length());
		}
		users.increment(userID);

		mainTable.setHeader("SubjectID", userID);
		mainTable.setHeader("AttemptID", attempt.id);


		Snapshot lastSnapshot = null;
		String lastCode = "";

		for (int i = 0; i < attempt.size(); i++) {
			AttemptAction action = attempt.rows.get(i);

			String EventType = null;
			String EventID = null;
			Integer Order = null;
			String CodeStateID = null;
			String ParentEventID = null;
			String ClientTimestamp = null;
			String ClientTimezone = null;
			String SessionID = null;
			String CourseID = null;
			String TermID = null;
			String AssignmentID = null;
			String ExperimentalCondition = null;
			String CodeStateSection = null;
			String EventInitiator = null;
			String EditType = null;
			String InterventionType = null;
			String InterventionMessage = null;



			String message = action.message;
			String data = action.data;

			String studentResponse = "ATTEMPT";
			String selection = "";
			String feedbackText = "";
			String feedbackClassification = "";
			String code = "";
			String humanReadableCode = "";

			if (action.snapshot != null) {
				ASTNode ast = JsonAST.toAST(action.snapshot, false);
				String newCode = ast.prettyPrint(true, RatingConfig.Snap);
				if (!newCode.equals(lastCode)) {
					code = ast.toJSON().toString();
					lastCode = humanReadableCode = newCode.replace("\n", "\\n");
					lastSnapshot = action.snapshot;
				}
			}

			if (action.data != null && data.startsWith("{")) {
				JSONObject jsonData = new JSONObject(data);
				if (jsonData.has("id") && jsonData.get("id") instanceof JSONObject) {
					jsonData = jsonData.getJSONObject("id");
				}
				if (jsonData.has("selector") && jsonData.has("id")) {
					String id = jsonData.get("id").toString();
					selection = id + ";" + jsonData.get("selector");
				} else if (jsonData.has("guid")) {
					selection = jsonData.getString("guid");
				}
			}
			if (data.startsWith("\"") && data.endsWith("\"")) {
				data = data.substring(1, data.length() - 1);
			}
			if (AttemptAction.SINGLE_ARG_MESSAGES.contains(message)) {
				selection = data;
			} else if (AttemptAction.SPRITE_ADD_VARIABLE.contains(message) ||
					AttemptAction.SPRITE_DELETE_VARIABLE.contains(message)) {
				selection = data;
			} else if (AttemptAction.IDE_ADD_SPRITE.equals(message) ||
					AttemptAction.IDE_REMOVE_SPRITE.equals(message) ||
					AttemptAction.IDE_SELECT_SPRITE.equals(message)) {
				selection = data;
			} else if (AttemptAction.HINT_DIALOG_LOG_FEEDBACK.equals(message)) {
				if (data.length() > 4) {
					selection = data.substring(2, data.length() - 2);
				}
			} else if (AttemptAction.SCRIPTS_UNDROP.equals(message) || AttemptAction.SCRIPTS_REDROP.equals(message)) {
				JSONObject jsonData = new JSONObject(data);
				selection = String.valueOf(jsonData.opt("block"));
			} else if (message.matches("HighlightDisplay\\.((show)|(hide)).*Insert")) {
				JSONObject jsonData = new JSONObject(data);
				JSONObject candidate = jsonData.getJSONObject("candidate");
				selection = candidate.getInt("id") + ";" + candidate.getString("selector");
			} else if (AttemptAction.SHOW_HINT_MESSAGES.contains(message) && lastSnapshot != null) {
				studentResponse = "HINT_REQUEST";
				feedbackClassification = message;

				JSONObject jsonData = new JSONObject(data);
				Node root = SimpleNodeBuilder.toTree(lastSnapshot, true);

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

				String[] from = new String[fromArray.length()];
				for (int j = 0; j < from.length; j++) from[j] = fromArray.getString(j);

				Node parent = CheckHintUsage.findParent(
						message, lastSnapshot, root, jsonData, from);
				if (parent == null) {
					parent = CheckHintUsage.checkForZombieHintParent(attempt, jsonData, from, i);
				}
				if (parent == null) System.err.println("Null parent: " + data);

				Integer scriptIndex = -1;
				String parentID = null;
				if (parent != null) {
					scriptIndex = -1;
					while (parent.tag instanceof Script || !(parent.tag instanceof IHasID)) {
						if (parent.tag instanceof Script || parent.tag instanceof ListBlock) {
							scriptIndex = parent.index();
						} else {
							out.println("No ID: " + parent.type());
						}
						parent = parent.parent;
					}
					parentID = ((IHasID)parent.tag).getID();
					if (parentID == null) {
						System.err.println("No parentID: " + parent.type());
					} else {
						selection = parentID;
					}
				}

				JSONObject saveData = new JSONObject();
				saveData.put("parentID", parentID);
				saveData.put("parentType", parent == null ? null : parent.type());
				if (scriptIndex >= 0) {
					// Because scripts have no IDs, we use their parents' IDs, and mark
					// which script was referenced
					saveData.put("scriptIndex", scriptIndex);
				}
				saveData.put("from", fromArray);
				saveData.put("to", toArray);
				if (jsonData.has("message")) {
					saveData.put("message", jsonData.get("message"));
				}

				feedbackText = saveData.toString();
			} else if (selection.length() == 0 && data.length() > 0) {
//				out.println(message + ": " + data);
				unexportedMessages.add(message);
			}

			for (String toStrip : JsonAST.valueReplacements.keySet()) {
				if (selection.contains(toStrip)) {
					selection = selection.replace(toStrip, JsonAST.valueReplacements.get(toStrip));
				}
			}

			String stepTarget = selection;
			int stepSemi = stepTarget.indexOf(";");
			if (stepSemi >= 0) stepTarget = stepTarget.substring(0, stepSemi);
			String stepName = message + (stepTarget.length() > 0 ? "_" : "") + stepTarget;

//			printer.printRecord(new Object[] {
//					userID,
//					attemptID + "_" + action.sessionID,
//					action.timestamp.getTime(),
//					studentResponse,
//					levelType,
//					problemName,
//					stepName,
//					selection,
//					message,
//					message,
//					feedbackText,
//					feedbackClassification,
//					code,
//					humanReadableCode,
//			});
		}
	}
}
