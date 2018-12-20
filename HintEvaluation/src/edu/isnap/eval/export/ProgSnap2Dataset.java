package edu.isnap.eval.export;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

	public static void main(String[] args) throws IOException {
		exportAndWrite(Spring2017.GuessingGame1);
	}

	private final String dir;
	private final Spreadsheet mainTable;
	private final Spreadsheet codeStateTable;
	private final Spreadsheet assignmentLinkTable;

	private final Set<String> unexportedMessages = new LinkedHashSet<>();
	private final CountMap<String> users = new CountMap<>();

	private final List<Event> rows = new ArrayList<>();
	private final Map<String, Integer> codeStates = new HashMap<>();

	public PrintStream out = System.out;

	public static void exportAndWrite(Assignment assignment)
			throws FileNotFoundException, IOException {
		ProgSnap2Dataset progsnap = new ProgSnap2Dataset(assignment.exportDir());
		progsnap.export(assignment);
		progsnap.close();
	}

	public static void exportAndWrite(Dataset dataset)
			throws FileNotFoundException, IOException {
		ProgSnap2Dataset progsnap = new ProgSnap2Dataset(dataset.exportDir());
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

		codeStateTable = new Spreadsheet(CSVFormat.RFC4180);
		codeStateTable.beginWrite(dir + "/ProgSnap2/CodeStates/CodeStates.csv");
	}

	@Override
	public void close() throws IOException {
		Collections.sort(rows);
		for (int order = 0; order < rows.size(); order++) {
			rows.get(order).write(mainTable, order);
		}

		mainTable.endWrite();
		codeStateTable.endWrite();
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
		assignmentLinkTable.put("AssignmentID", assignment.name);
		assignmentLinkTable.put("URL", "file:///Resources/Assignments.pdf");
		assignmentLinkTable.put("Type", assignment.name.contains("HW") ? "Homework" : "In-Lab");

		Map<String, AssignmentAttempt> attempts = assignment.load(Mode.Use, false, true,
				new SnapParser.SubmittedOnly());
		for (AssignmentAttempt attempt : attempts.values()) {
			if (attempt.submittedActionID == AssignmentAttempt.NOT_SUBMITTED) continue;
			out.println(attempt.id);
			export(assignment, attempt, mainTable);
		}
	}

	private class Event implements Comparable<Event> {
		private final DateFormat DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		private final DateFormat TimeZoneFormat = new SimpleDateFormat("Z");

		private final Assignment assignment;
		private final AttemptAction action;
		private Integer EventID = null;

		// Required columns
		public final String EventType;
		public final String SubjectID;
		public final Integer CodeStateID;

		// Optional columns
		public Event ParentEvent = null;
		public String CodeStateSection = null;
		public String EventInitiator = null;
		public String EditType = null;
		public String InterventionType = null;
		public String InterventionMessage = null;

		public Event(Assignment assignment, AssignmentAttempt attempt, AttemptAction action,
				String eventType, String code, String pseudocode) {
			this.assignment = assignment;
			this.action = action;
			this.EventType = eventType;

			String subjectID = attempt.userID();
			if (subjectID == null || subjectID.isEmpty()) {
				subjectID = "PROJECT_ONLY_" + attempt.id;
				out.println("No UID: " + attempt.id);
			} if (subjectID.length() > 16) {
				subjectID = subjectID.substring(subjectID.length() - 16, subjectID.length());
			}
			users.increment(subjectID);
			this.SubjectID = subjectID;

			Integer codeStateID = codeStates.get(code);
			if (codeStateID == null) {
				codeStateID = codeStates.size();
				codeStates.put(code, codeStateID);

				codeStateTable.newRow();
				codeStateTable.put("CodeStateID", codeStateID);
				codeStateTable.put("Code", code);
				codeStateTable.put("Pseudocode", pseudocode);
			}
			this.CodeStateID = codeStateID;
		}

		public void write(Spreadsheet spreadsheet, int order) {
			if (ParentEvent != null && ParentEvent.EventID == null) {
				throw new RuntimeException("Parents should always be logged before children!");
			}

			EventID = order;

			spreadsheet.newRow();
			spreadsheet.put("EventType", EventType);
			spreadsheet.put("EventID", EventID);
			spreadsheet.put("Order", order);
			spreadsheet.put("SubjectID", SubjectID);
			spreadsheet.put("Toolnstances", assignment.dataset.getToolInstances());
			spreadsheet.put("CodeStateID", CodeStateID);
			spreadsheet.put("ParentEventID", ParentEvent == null ? null : ParentEvent.EventID);
			spreadsheet.put("ClientTimestamp", DateFormat.format(action.timestamp));
			spreadsheet.put("ClientTimezone", TimeZoneFormat.format(action.timestamp));
			spreadsheet.put("SessionID", action.sessionID);
			spreadsheet.put("CourseID", "UnivNonMajorsIntro"); // TODO: Don't hard code
			spreadsheet.put("TermID", assignment.dataset.getName());
			spreadsheet.put("AssignmentID", assignment.name);
			spreadsheet.put("CodeStateSection", CodeStateSection);
			spreadsheet.put("EventInitiator", EventInitiator);
			spreadsheet.put("EditType", EditType);
			spreadsheet.put("InterventionType", InterventionType);
			spreadsheet.put("InterventionMessage", InterventionMessage);
		}

		private Comparator<Event> comparator =
				Comparator.comparing((Event e) -> e.action.timestamp)
					.thenComparing(e -> e.action.id)
					.thenComparing(e -> e.ParentEvent == null ? -1 : 1);

		@Override
		public int compareTo(Event o) {
			return comparator.compare(this, o);
		}
	}

	private void export(Assignment assignment, AssignmentAttempt attempt, Spreadsheet spreadsheet)
			throws IOException {

		Snapshot lastSnapshot = null;
		String lastCode = "";

		for (int i = 0; i < attempt.size(); i++) {
			AttemptAction action = attempt.rows.get(i);

			String message = action.message;
			String data = action.data;

			String selection = "";

			String humanReadableCode = "";

			if (action.snapshot != null) {
				ASTNode ast = JsonAST.toAST(action.snapshot, false);
				String newCode = ast.prettyPrint(true, RatingConfig.Snap);
				if (!newCode.equals(lastCode)) {
					lastCode = ast.toJSON().toString();
					humanReadableCode = newCode;
					lastSnapshot = action.snapshot;
				}
			}

			String EventType = action.message; // TODO: Change
			Event mainEvent = new Event(assignment, attempt, action, EventType, lastCode,
					humanReadableCode);
			rows.add(mainEvent);

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

				mainEvent.InterventionType = message.replace("SnapDisplay.", "");

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

				mainEvent.InterventionMessage = saveData.toString();
			} else if (selection.length() == 0 && data.length() > 0) {
//				out.println(message + ": " + data);
				unexportedMessages.add(message);
			}

			for (String toStrip : JsonAST.valueReplacements.keySet()) {
				if (selection.contains(toStrip)) {
					selection = selection.replace(toStrip, JsonAST.valueReplacements.get(toStrip));
				}
			}

			mainEvent.ParentEvent = null;
			mainEvent.CodeStateSection = selection;
			mainEvent.EventInitiator = null;
			mainEvent.EditType = null;
		}
	}
}
