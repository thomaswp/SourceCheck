package edu.isnap.eval.export;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.csv.CSVFormat;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.isnap.dataset.Assignment;
import edu.isnap.dataset.AssignmentAttempt;
import edu.isnap.dataset.AttemptAction;
import edu.isnap.dataset.AttemptAction.ActionData;
import edu.isnap.dataset.Dataset;
import edu.isnap.datasets.Spring2017;
import edu.isnap.eval.user.CheckHintUsage;
import edu.isnap.hint.util.SimpleNodeBuilder;
import edu.isnap.node.ASTNode;
import edu.isnap.node.INode;
import edu.isnap.node.Node;
import edu.isnap.parser.SnapParser;
import edu.isnap.parser.Store.Mode;
import edu.isnap.parser.elements.Script;
import edu.isnap.parser.elements.util.IHasID;
import edu.isnap.rating.RatingConfig;
import edu.isnap.util.Spreadsheet;
import edu.isnap.util.map.CountMap;

public class ProgSnap2Dataset implements Closeable {

	public static void main(String[] args) throws IOException {
		exportAndWrite(Spring2017.GuessingGame1);
	}

	private final static CSVFormat CSV_FORMAT = CSVFormat.RFC4180;
	private final static String OUTPUT_DIR = "ProgSnap2";

	// Metadata
	private final static int PROGSNAP_VERSION = 5;
	private final static boolean IS_EVENT_ORDERING_CONSISTENT = true;
	private final static String EVENT_ORDER_SCOPE = "Restricted";
	private final static String EVENT_ORDER_SCOPE_COLUMNS =
			"TermID;CourseID;AssignmentID;SubjectID";
	private final static String CODE_STATE_REPRESENTATION = "Table";


	private final static Set<String> SECTION_TYPES = new HashSet<>(Arrays.asList(
			new String[] {
					"sprite", "stage", "customBlock", "snapshot", "Snap!shot"
			}));

	private final String dir;
	private final Spreadsheet mainTable;
	private final Spreadsheet codeStateTable;
	private final Spreadsheet assignmentLinkTable;

	private final Set<String> unexportedMessages = new TreeSet<>();
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
		mainTable = new Spreadsheet(CSV_FORMAT);
		mainTable.beginWrite(getPath("MainTable.csv"));

		assignmentLinkTable = new Spreadsheet(CSV_FORMAT);
		assignmentLinkTable.beginWrite(getPath("LinkTables/Assignment.csv"));

		codeStateTable = new Spreadsheet(CSV_FORMAT);
		codeStateTable.beginWrite(getPath("CodeStates/CodeStates.csv"));
	}

	private String getPath(String path) {
		return Paths.get(dir, OUTPUT_DIR, path).toFile().getPath();
	}

	@Override
	public void close() throws IOException {
		writeMetaData();

//		Collections.sort(rows);
		for (int eventID = 0; eventID < rows.size(); eventID++) {
			rows.get(eventID).write(mainTable, eventID);
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

	private void writeMetaData() throws IOException {
		Spreadsheet metadataTable = new Spreadsheet(CSV_FORMAT);
		metadataTable.beginWrite(getPath("DatasetMetadata.csv"));
		writeKeyValue(metadataTable, "Version", PROGSNAP_VERSION);
		writeKeyValue(metadataTable, "IsEventOrderingConsistent", IS_EVENT_ORDERING_CONSISTENT);
		writeKeyValue(metadataTable, "EventOrderingScope", EVENT_ORDER_SCOPE);
		writeKeyValue(metadataTable, "EventOrderingScopeColumns", EVENT_ORDER_SCOPE_COLUMNS);
		writeKeyValue(metadataTable, "CodeStateRepresentation", CODE_STATE_REPRESENTATION);
		metadataTable.endWrite();
	}

	private void writeKeyValue(Spreadsheet spreadsheet, String key, Object value) {
		spreadsheet.newRow();
		spreadsheet.put("Property", key);
		spreadsheet.put("Value", value);
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

	private class Event {
		private final DateFormat DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		private final DateFormat TimeZoneFormat = new SimpleDateFormat("Z");

		private final Assignment assignment;
		private final AttemptAction action;

		// Required columns
		private Integer eventID = null;
		public final String eventType;
		public final String subjectID;
		public final Integer codeStateID;
		public final Integer order;

		// Optional columns
		public String projectID;
		public Event parentEvent;
		public String resourceID; // For Block.showHelp events
		public String codeStateSection;
		public String eventInitiator = "User"; // Default to user-initiation, and override if needed
		public String editType;
		public String interventionType;
		public String interventionMessage;
		public String newSpriteName;
		public String editTrigger;
		public String sourceLocation;
		public Double score;
		public String programErrorOutput;

		// X-Columns
		public String editSubtype;
		public String blockSelector;
		public String blockID;
		public String blockCategorySelected;
		public String hintData;

		public Event(int order, Assignment assignment, AssignmentAttempt attempt,
				AttemptAction action, String eventType, String code, String pseudocode) {
			this.assignment = assignment;
			this.action = action;
			this.eventType = eventType;
			this.order = order;
			this.projectID = attempt.id;

			String subjectID = attempt.userID();
			if (subjectID == null || subjectID.isEmpty()) {
				subjectID = "PROJECT_ONLY_" + attempt.id;
				out.println("No UID: " + attempt.id);
			} if (subjectID.length() > 16) {
				subjectID = subjectID.substring(subjectID.length() - 16, subjectID.length());
			}
			users.increment(subjectID);
			this.subjectID = subjectID;

			Integer codeStateID = codeStates.get(code);
			if (codeStateID == null) {
				codeStateID = codeStates.size();
				codeStates.put(code, codeStateID);

				codeStateTable.newRow();
				codeStateTable.put("CodeStateID", codeStateID);
				codeStateTable.put("Code", code);
				codeStateTable.put("Pseudocode", pseudocode);
			}
			this.codeStateID = codeStateID;
		}

		public void write(Spreadsheet spreadsheet, int eventID) {
			if (parentEvent != null && parentEvent.eventID == null) {
				throw new RuntimeException("Parents should always be logged before children!");
			}

			this.eventID = eventID;

			spreadsheet.newRow();
			// Required Columns
			spreadsheet.put("EventType", eventType);
			spreadsheet.put("EventID", this.eventID);
			spreadsheet.put("Order", order);
			spreadsheet.put("SubjectID", subjectID);
			spreadsheet.put("Toolnstances", assignment.dataset.getToolInstances());
			spreadsheet.put("CodeStateID", codeStateID);
			// Recommended All-Event Columns
			spreadsheet.put("ClientTimestamp", DateFormat.format(action.timestamp));
			spreadsheet.put("ClientTimezone", TimeZoneFormat.format(action.timestamp));
			spreadsheet.put("SessionID", action.sessionID);
			spreadsheet.put("ProjectID", projectID);
			spreadsheet.put("CourseID", assignment.dataset.courseID());
			spreadsheet.put("TermID", assignment.dataset.getName());
			spreadsheet.put("AssignmentID", assignment.name);
			// Event-specific Columns
			spreadsheet.put("ParentEventID", parentEvent == null ? null : parentEvent.eventID);
			spreadsheet.put("ResourceID", resourceID);
			spreadsheet.put("CodeStateSection", codeStateSection);
			spreadsheet.put("EventInitiator", eventInitiator);
			spreadsheet.put("NewFileLocation", newSpriteName);
			spreadsheet.put("EditType", editType);
			spreadsheet.put("EditTrigger", editTrigger);
			spreadsheet.put("SourceLocation", sourceLocation);
			spreadsheet.put("Score", score);
			spreadsheet.put("ProgramErrorOutput", programErrorOutput);
			spreadsheet.put("InterventionType", interventionType);
			spreadsheet.put("InterventionMessage", interventionMessage);
			// X-Columns
			spreadsheet.put("X-EditSubtype", editSubtype);
			spreadsheet.put("X-BlockID", blockID);
			spreadsheet.put("X-BlockSelector", blockSelector);
			spreadsheet.put("X-BlockCategorySelected", blockCategorySelected);
			spreadsheet.put("X-HintData", hintData);
		}

//		private Comparator<Event> comparator =
//				Comparator.comparing((Event e) -> e.action.timestamp)
//					.thenComparing(e -> e.action.id)
//					.thenComparing(e -> e.ParentEvent == null ? -1 : 1);
//
//		@Override
//		public int compareTo(Event o) {
//			return comparator.compare(this, o);
//		}
	}

	private interface DataConsumer {
		void read(Event event, ActionData data);
	}

	private class EventConverter {
		final String eventType;
		final String[] actions;
		private final List<DataConsumer> dataConsumers = new ArrayList<>();

		EventConverter(String eventType, String... actions) {
			this.eventType = eventType;
			this.actions = actions;
		}

		EventConverter addData(DataConsumer dataConsumer) {
			dataConsumers.add(dataConsumer);
			return this;
		}

		Event createEvent(int order, Assignment assignment, AssignmentAttempt attempt,
				AttemptAction action, String code, String pseudocode) {
			Event event = new Event(order, assignment, attempt, action, eventType, code,
					pseudocode);
			dataConsumers.forEach(c -> c.read(event, action.getData()));
			return event;
		}
	}

	private final Map<String, EventConverter> converters = new HashMap<>();
	{
		DataConsumer getSpriteName = (Event event, ActionData data) -> {
			event.codeStateSection  = "sprite:" + data.asString(); // TODO: need to filter values
		};

		List<EventConverter> converters = new ArrayList<>();

		converters.add(new EventConverter("Project.Open",
				AttemptAction.IDE_OPEN_PROJECT,
				AttemptAction.IDE_OPEN_PROJECT_STRING,
				AttemptAction.IDE_NEW_PROJECT));

		converters.add(new EventConverter("File.Create",
				AttemptAction.IDE_ADD_SPRITE,
				AttemptAction.IDE_DUPLICATE_SPRITE)
				.addData(getSpriteName));

		converters.add(new EventConverter("File.Delete",
				AttemptAction.IDE_REMOVE_SPRITE)
				.addData(getSpriteName));

		converters.add(new EventConverter("File.Focus",
				AttemptAction.IDE_SELECT_SPRITE)
				.addData(getSpriteName));

		converters.add(new EventConverter("File.Rename",
				AttemptAction.SPRITE_SET_NAME)
				.addData((Event event, ActionData data) -> {
						// TODO: need to filter values
						event.newSpriteName = "sprite: " + data.asString();
					}
				));

		Map<String, String> editActionMap = new HashMap<>();
		// Block edit events
		editActionMap.put(AttemptAction.BLOCK_CREATED, "Insert");
		editActionMap.put(AttemptAction.BLOCK_DUPLICATE_ALL, "Paste");
		editActionMap.put(AttemptAction.BLOCK_DUPLICATE_BLOCK, "Paste");
		editActionMap.put(AttemptAction.BLOCK_GRABBED, "Move");
		editActionMap.put(AttemptAction.BLOCK_SNAPPED, "Move");
		editActionMap.put(AttemptAction.BLOCK_USER_DESTROY, "Delete");
		editActionMap.put(AttemptAction.BLOCK_DRAG_DESTROY, "Delete");
		editActionMap.put(AttemptAction.BLOCK_RELABEL, "Replace");
		editActionMap.put(AttemptAction.BLOCK_RENAME, "Replace");
		editActionMap.put(AttemptAction.BLOCK_RINGIFY, "Insert");
		editActionMap.put(AttemptAction.BLOCK_UNRINGIFY, "Delete");
		editActionMap.put(AttemptAction.BLOCK_REFACTOR_VAR, "Replace");
		// Input (including multiarg and template) events (which also have block IDs)
		editActionMap.put(AttemptAction.INPUT_SLOT_EDITED, "Replace");
		editActionMap.put(AttemptAction.INPUT_SLOT_MENU_ITEM_SELECTED, "Replace");
		editActionMap.put(AttemptAction.MULTI_ARG_ADD_INPUT, "Insert");
		editActionMap.put(AttemptAction.MULTI_ARG_REMOVE_INPUT, "Delete");
		editActionMap.put(AttemptAction.TEMPLATE_ARG_RENAME, "Replace");
		// Variables
		editActionMap.put(AttemptAction.SPRITE_ADD_VARIABLE, "Insert");
		editActionMap.put(AttemptAction.SPRITE_DELETE_VARIABLE, "Delete");

		String[] blockEditActions = editActionMap.keySet().stream()
				.toArray(x -> new String[x]);

		DataConsumer getBlockIDAndSelector = (Event event, ActionData data) -> {
			event.blockID = data.getBlockID();
			event.blockSelector = data.getSelector();
		};

		converters.add(new EventConverter("File.Edit", blockEditActions)
				.addData(getBlockIDAndSelector)
				.addData((Event event, ActionData data) -> {
						event.editType = editActionMap.get(data.parent.message);
						event.editTrigger = "SubjectDirectAction";
						event.editSubtype = data.parent.message;

					}
				));

		Map<String, String> customBlockEditActionMap = new HashMap<>();
		customBlockEditActionMap.put(AttemptAction.BLOCK_EDITOR_APPLY, "Replace");
		customBlockEditActionMap.put(AttemptAction.BLOCK_EDITOR_CANCEL, "Replace");
		customBlockEditActionMap.put(AttemptAction.BLOCK_EDITOR_START, "Insert");
		customBlockEditActionMap.put(AttemptAction.IDE_DELETE_CUSTOM_BLOCK, "Delete");

		String[] customBlockEditActions = customBlockEditActionMap.keySet().stream()
				.toArray(x -> new String[x]);

		converters.add(new EventConverter("File.Edit", customBlockEditActions)
				.addData((Event event, ActionData data) -> {
						event.editType = customBlockEditActionMap.get(data.parent.message);
						event.editTrigger = "SubjectDirectAction";
						event.editSubtype = data.parent.message;
						String guid = data.getBlockDefinitionGUID();
						if (guid != null) event.codeStateSection = "customBlock:" + guid;
					}
				));

		converters.add(new EventConverter("File.Save", // TODO: Confirm whether X-File.Save
				AttemptAction.IDE_EXPORT_PROJECT,
				AttemptAction.IDE_SAVE_PROJECT));

		converters.add(new EventConverter("Resource.View",
				AttemptAction.BLOCK_SHOW_HELP)
				.addData((Event event, ActionData data) -> {
						event.resourceID = data.getSelector();
					}
				));

		// TODO: Get and add run errors
		converters.add(new EventConverter("Run.Program",
				AttemptAction.BLOCK_CLICK_RUN,
				AttemptAction.IDE_GREEN_FLAG_RUN)
				.addData(getBlockIDAndSelector));

		// X-events:

		converters.add(new EventConverter("X-Run.Stop",
				AttemptAction.BLOCK_CLICK_STOP_RUN,
				AttemptAction.IDE_STOP)
				.addData(getBlockIDAndSelector));
		converters.add(new EventConverter("X-Run.Pause", AttemptAction.IDE_PAUSE));
		converters.add(new EventConverter("X-Run.Unpause", AttemptAction.IDE_UNPAUSE));

		converters.add(new EventConverter("X-ChangeBlockCategory",
				AttemptAction.IDE_CHANGE_CATEGORY)
				.addData((Event event, ActionData data) -> {
						event.blockCategorySelected = data.asString();
					}
				));

		converters.add(new EventConverter("Intervention",
				AttemptAction.SHOW_HINT_MESSAGES.stream().toArray(x -> new String[x]))
				.addData((Event event, ActionData data) -> {
						event.eventInitiator = "User";
						event.interventionType = data.parent.message
								.replace("SnapDisplay.show", "");
						event.interventionMessage = data.getTextHint();
					}
				));

		// TODO: Handle other hints/intervention
//		HighlightDialogBoxMorph.cancelShowOnRun
//		HighlightDialogBoxMorph.destroy
//		HighlightDialogBoxMorph.promptShowOnRun
//		HighlightDialogBoxMorph.showOnRun
//		HighlightDialogBoxMorph.toggleAutoClear
//		HighlightDialogBoxMorph.toggleInsert
//		HighlightDialogBoxMorph.toggleShowOnRun
//		HighlightDisplay.autoClear
//		HighlightDisplay.checkMyWork
//		HighlightDisplay.informNoHints
//		HighlightDisplay.promptShowBlockHints
//		HighlightDisplay.promptShowInserts
//		HighlightDisplay.showInsertsFromPrompt
//		HighlightDisplay.startHighlight
//		HighlightDisplay.stopHighlight
//		HintDialogBox.destroy
//		HintProvider.processHints

		for (EventConverter converter : converters) {
			for (String action : converter.actions) {
				EventConverter old = this.converters.put(action, converter);
				if (old != null) throw new RuntimeException("DULPICATE CONVERTER: " + action);
			}
		}
	}

	private void export(Assignment assignment, AssignmentAttempt attempt, Spreadsheet spreadsheet)
			throws IOException {

		ASTNode lastASTNode = null;
		String lastCode = "";
		String lastSessionID = "";

		int order = 0;
		for (int i = 0; i < attempt.size(); i++) {
			AttemptAction action = attempt.rows.get(i);

			String message = action.message;
			String humanReadableCode = "";

			if (action.snapshot != null) {
				lastASTNode = JsonAST.toAST(action.snapshot, false);
				String newCode = lastASTNode.prettyPrint(true, RatingConfig.Snap);
				if (!newCode.equals(lastCode)) {
					lastCode = lastASTNode.toJSON().toString();
					humanReadableCode = newCode;
				}
			}

			if (!action.sessionID.equals(lastSessionID)) {
				rows.add(new Event(order++, assignment, attempt, action, "Session.Start",
						lastCode, humanReadableCode));
			}
			lastSessionID = action.sessionID;

			if (message.equals(AttemptAction.IDE_OPEN_PROJECT) ||
					message.equals(AttemptAction.IDE_OPEN_PROJECT_STRING)) {
				rows.add(new Event(order++, assignment, attempt, action, "Project.Close",
						lastCode, humanReadableCode));
			}

			Event event = null;
			if (!converters.containsKey(message)) {
				unexportedMessages.add(action.message);
			} else {

				event = converters.get(message).createEvent(order, assignment, attempt,
						action, lastCode, humanReadableCode);
				rows.add(event);
			}

			if (event != null && event.blockID != null && lastASTNode != null) {
				String blockID = event.blockID;
				INode block = lastASTNode.search(
						node -> blockID.equals(node.id())
				);
				if (block != null) {
					event.sourceLocation = getSourceLocation(block, lastASTNode);
					event.codeStateSection = getCodeStateSection(block, lastASTNode);
					if (event.blockSelector == null) event.blockSelector = block.type();
				}
			}

			if (event != null && AttemptAction.SHOW_HINT_MESSAGES.contains(message)) {
				JSONObject jsonData = new JSONObject(action.data);
				Node root = SimpleNodeBuilder.toTree(action.lastSnapshot, true);
				Node parent = CheckHintUsage.findParent(message, action.lastSnapshot, root,
						jsonData, new String[0]);
				if (parent == null) System.err.println("Null parent: " + action.data);
				int scriptOrListIndex = -1;
				while (parent.tag instanceof Script || !(parent.tag instanceof IHasID)) {
					if (parent.hasType("list", "script")) {
						scriptOrListIndex = parent.index();
					} else {
						System.out.println("No ID: " + parent.type());
					}
					parent = parent.parent;
				}
				String parentID = ((IHasID) parent.tag).getID();

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
				if (scriptOrListIndex >= 0) {
					// Because scripts have no IDs, we use their parents' IDs, and mark which script
					// was referenced
					saveData.put("scriptOrListIndex", scriptOrListIndex);
				}
				saveData.put("from", fromArray);
				saveData.put("to", toArray);
				event.hintData = saveData.toString();
			}

			// TODO: More careful anonymization
//			for (String toStrip : JsonAST.valueReplacements.keySet()) {
//				if (selection.contains(toStrip)) {
//					selection = selection.replace(toStrip, JsonAST.valueReplacements.get(toStrip));
//				}
//			}

			if (i + 1 >= attempt.rows.size() ||
					!attempt.rows.rows.get(i + 1).sessionID.equals(lastSessionID)) {
				rows.add(new Event(order++, assignment, attempt, action, "Session.End",
						lastCode, humanReadableCode));
			}

			if (i == attempt.rows.size() - 1) {
				rows.add(new Event(order++, assignment, attempt, action, "Project.Close",
						lastCode, humanReadableCode));
				if (attempt.isSubmitted()) {
					Event submitEvent = new Event(order++, assignment, attempt, action,
							"Project.Submit", lastCode, humanReadableCode);
					rows.add(submitEvent);
					if (attempt.grade != null) {
						submitEvent.score = attempt.grade.average(); // TODO: Get actual grade
					}
				}
			}
		}
	}

	private static String getCodeStateSection(INode node, ASTNode root) {
		while (node != null && !SECTION_TYPES.contains(node.type())) {
			node = node.parent();
		}
		if (node == null) return null;
		return node.type() + ":" + ("customBlock".equals(node.type()) ? node.id() : node.value());
	}

	private String getSourceLocation(INode node, INode root) {
		if (node == null) return null;
		String location = "";
		while (node.parent() != null) {
			location = ":" + node.index() + location;
			node = node.parent();
		}
		location = "Tree" + location;
		return location;
	}
}
