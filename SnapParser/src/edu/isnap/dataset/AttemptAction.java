package edu.isnap.dataset;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONObject;

import edu.isnap.parser.elements.Snapshot;

public class AttemptAction implements Serializable, Comparable<AttemptAction> {
	private static final long serialVersionUID = 1L;

	public final static String ERROR = "Error";
	public static final String ASSIGNMENT_SET_ID_FROM = "Assignment.setIDFrom";
	public static final String ASSIGNMENT_SET_ID = "Assignment.setID";
	public final static String BLOCK_CLICK_RUN = "Block.clickRun";
	public final static String BLOCK_EDITOR_START = "BlockEditor.start";
	public final static String BLOCK_EDITOR_APPLY = "BlockEditor.apply";
	public final static String BLOCK_EDITOR_OK = "BlockEditor.ok";
	public final static String BLOCK_EDITOR_CANCEL = "BlockEditor.cancel";
	public final static String BLOCK_EDITOR_CHANGE_TYPE = "BlockEditor.changeType";
	public final static String BLOCK_GRABBED = "Block.grabbed";
	public final static String BLOCK_DUPLICATE_ALL = "Block.duplicateAll";
	public final static String BLOCK_DUPLICATE_BLOCK = "Block.duplicateBlock";
	public final static String BLOCK_CREATED = "Block.created";
	public final static String BLOCK_SNAPPED = "Block.snapped";
	public final static String BLOCK_USER_DESTROY = "Block.userDestroy";
	public final static String BLOCK_DRAG_DESTROY = "Block.dragDestroy";
	public final static String BLOCK_RELABEL = "Block.relabel";
	public final static String BLOCK_RENAME = "Block.rename";
	public final static String BLOCK_RINGIFY = "Block.ringify";
	public final static String BLOCK_UNRINGIFY = "Block.unringify";
	public final static String BLOCK_REFACTOR_VAR = "Block.refactorVar";
	public final static String BLOCK_SHOW_HELP = "Block.showHelp";
	public static final String IDE_ADD_SPRITE = "IDE.addSprite";
	public static final String IDE_CHANGE_CATEGORY = "IDE.changeCategory";
	public final static String IDE_GREEN_FLAG_RUN = "IDE.greenFlag";
	public final static String IDE_EXPORT_PROJECT = "IDE.exportProject";
	public final static String IDE_NEW_PROJECT = "IDE.newProject";
	public final static String IDE_OPEN_PROJECT = "IDE.openProject";
	public final static String IDE_OPEN_PROJECT_STRING = "IDE.openProjectString";
	public final static String IDE_OPENED = "IDE.opened";
	public static final String IDE_REMOVE_SPRITE = "IDE.removeSprite";
	public static final String IDE_SELECT_SPRITE = "IDE.selectSprite";
	public static final String IDE_SET_SPRITE_TAB = "IDE.setSpriteTab";
	public static final String IDE_TOGGLE_APP_MODE = "IDE.toggleAppMode";
	public static final String IDE_DUPLICATE_SPRITE = "IDE.duplicateSprite";
	public static final String IDE_PAINT_NEW_SPRITE = "IDE.paintNewSprite";
	public final static String HELP_BUTTON_TOGGLED = "HelpButton.toggled";
	public final static String HIGHLIGHT_CHECK_WORK = "HighlightDisplay.checkMyWork";
	public final static String HIGHLIGHT_TOGGLE_INSERT = "HighlightDialogBoxMorph.toggleInsert";
	public final static String HINT_DIALOG_DESTROY = "HintDialogBox.destroy";
	public final static String HINT_DIALOG_DONE = "HintDialogBox.done";
	public final static String HINT_DIALOG_NEXT = "HintDialogBoxMorph.prototype.next";
	public final static String HINT_DIALOG_LOG_FEEDBACK = "HintDialogBox.logFeedback";
	public final static String HINT_PROCESS_HINTS = "HintProvider.processHints";
	public final static String LOGGER_STARTED = "Logger.started";
	public final static String SCRIPTS_UNDROP = "Scripts.undrop";
	public final static String SCRIPTS_REDROP = "Scripts.redrop";
	public final static String SHOW_SCRIPT_HINT = "SnapDisplay.showScriptHint";
	public final static String SHOW_BLOCK_HINT = "SnapDisplay.showBlockHint";
	public final static String SHOW_STRUCTURE_HINT = "SnapDisplay.showStructureHint";
	public static final String SPRITE_ADD_VARIABLE = "Sprite.addVariable";
	public static final String SPRITE_DELETE_VARIABLE = "Sprite.deleteVariable";
	public static final String SPRITE_SET_NAME = "Sprite.setName";
	public final static String WE_START = "WE.Start";
	public final static String PROACTIVE_MIDSURVEY = "ProactiveDisplay.midSurveyShown";
	public final static String PROACTIVE_SEE_HINT = "ProactiveDisplay.seeHint";
	public final static String PROACTIVE_NO_HINT = "ProactiveDisplay.showNoAction";

	public final static Set<String> SHOW_HINT_MESSAGES = new HashSet<>(Arrays.asList(new String[] {
			SHOW_SCRIPT_HINT, SHOW_BLOCK_HINT, SHOW_STRUCTURE_HINT
	}));

	public final static Set<String> SINGLE_ARG_MESSAGES = new HashSet<>(Arrays.asList(new String[] {
			IDE_CHANGE_CATEGORY, IDE_SET_SPRITE_TAB, IDE_TOGGLE_APP_MODE, HELP_BUTTON_TOGGLED,
			BLOCK_EDITOR_CHANGE_TYPE, IDE_DUPLICATE_SPRITE, IDE_PAINT_NEW_SPRITE, SPRITE_SET_NAME,
			ASSIGNMENT_SET_ID_FROM, ASSIGNMENT_SET_ID,
	}));

	private final static DateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");



	public final int id;
	public final Snapshot snapshot;
	public final Date timestamp;
	public final String sessionID, message, data;

	/** Last snapshot saved when this action occurred (possiblly this action's snapshot). */
	public transient Snapshot lastSnapshot;
	/** The cumulative active time (in seconds) the student has spent after this action. */
	public transient int currentActiveTime;
	/**
	 * The assignmentID logged with this action, which may be different than that of the parent
	 * {@link AssignmentAttempt} if the attempt involved a change of assignment.
	 */
	public transient String loggedAssignmentID;

	private static Snapshot loadSnapshot(String attemptID, Date timestamp, String snapshotXML) {
		String name = attemptID;
		synchronized (format) {
			name += timestamp == null ? null : (": " + format.format(timestamp));
		}
		return Snapshot.parse(name, snapshotXML);
	}

	@SuppressWarnings("unused")
	private AttemptAction() {
		this(0, null, null, null, null, null);
	}

	public AttemptAction(int id, String attemptID, Date timestamp, String sessionID, String message,
			String data, String snapshotXML) {
		this(id, timestamp, sessionID, message, data,
				loadSnapshot(attemptID, timestamp, snapshotXML));
	}

	public AttemptAction(int id, Date timestamp, String sessionID, String message, String data,
			Snapshot snapshot) {
		this.id = id;
		this.timestamp = timestamp;
		this.sessionID = sessionID;
		this.message = message;
		this.data = data;
		this.snapshot = snapshot;
	}

	@Override
	public int compareTo(AttemptAction o) {
		if (timestamp == null) return o == null || o.timestamp == null ? 0 : -1;
		int tsc = timestamp.compareTo(o.timestamp);
		if (tsc != 0) return tsc;
		return Integer.compare(id, o.id);
	}

	public ActionData getData() {
		return new ActionData(data);
	}

	public class ActionData {
		public final String data;
		public final AttemptAction parent = AttemptAction.this;
		private final JSONObject jsonData;

		public ActionData(String dataString) {
			this.data = dataString;
			jsonData = (data == null || !data.startsWith("{")) ?
					null : new JSONObject(data);
		}

		public String asString() {
			if (data == null) return null;
			String data = this.data;
			if (data.startsWith("\"") && data.endsWith("\"")) {
				data = data.substring(1, data.length() - 1);
			}
			return data;
		}

		public String getID() {
			if (jsonData == null) return null;
			JSONObject jsonData = this.jsonData;
			if (jsonData.has("id") && jsonData.get("id") instanceof JSONObject) {
				jsonData = jsonData.getJSONObject("id");
			}
			if (jsonData.has("id")) {
				return jsonData.get("id").toString();
			} else if (jsonData.has("guid")) {
				return jsonData.getString("guid");
			}
			return null;
		}

		public String getSelector() {
			if (jsonData == null) return asString();
			JSONObject jsonData = this.jsonData;
			if (jsonData.has("id") && jsonData.get("id") instanceof JSONObject) {
				jsonData = jsonData.getJSONObject("id");
			}
			return jsonData.optString("selector");
		}

		public String getHintDialogFeedback() {
			if (data.length() > 4) {
				return data.substring(2, data.length() - 2);
			}
			return asString();
		}

		public String getBlock() {
			if (jsonData == null) return null;
			return String.valueOf(jsonData.opt("block"));
		}

	}
}
