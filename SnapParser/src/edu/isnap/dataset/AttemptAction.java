package edu.isnap.dataset;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import edu.isnap.parser.elements.Snapshot;

public class AttemptAction implements Serializable, Comparable<AttemptAction> {
	private static final long serialVersionUID = 1L;

	public final static String BLOCK_CLICK_RUN = "Block.clickRun";
	public final static String BLOCK_EDITOR_START = "BlockEditor.start";
	public final static String BLOCK_EDITOR_OK = "BlockEditor.ok";
	public final static String BLOCK_GRABBED = "Block.grabbed";
	public static final String IDE_ADD_SPRITE = "IDE.addSprite";
	public static final String IDE_CHANGE_CATEGORY = "IDE.changeCategory";
	public final static String IDE_GREEN_FLAG_RUN = "IDE.greenFlag";
	public final static String IDE_EXPORT_PROJECT = "IDE.exportProject";
	public static final String IDE_REMOVE_SPRITE = "IDE.removeSprite";
	public static final String IDE_SELECT_SPRITE = "IDE.selectSprite";
	public static final String IDE_SET_SPRITE_TAB = "IDE.setSpriteTab";
	public static final String IDE_TOGGLE_APP_MODE = "IDE.toggleAppMode";
	public final static String HELP_BUTTON_TOGGLED = "HelpButton.toggled";
	public final static String HINT_DIALOG_DESTROY = "HintDialogBox.destroy";
	public final static String HINT_DIALOG_DONE = "HintDialogBox.done";
	public final static String HINT_DIALOG_LOG_FEEDBACK = "HintDialogBox.logFeedback";
	public final static String HINT_PROCESS_HINTS = "HintProvider.processHints";
	public final static String SHOW_SCRIPT_HINT = "SnapDisplay.showScriptHint";
	public final static String SHOW_BLOCK_HINT = "SnapDisplay.showBlockHint";
	public final static String SHOW_STRUCTURE_HINT = "SnapDisplay.showStructureHint";
	public static final String SPRITE_ADD_VARIABLE = "Sprite.addVariable";
	public static final String SPRITE_DELETE_VARIABLE = "Sprite.deleteVariable";

	public final static Set<String> SHOW_HINT_MESSAGES = new HashSet<>(Arrays.asList(new String[] {
			SHOW_SCRIPT_HINT, SHOW_BLOCK_HINT, SHOW_STRUCTURE_HINT
	}));

	public final static Set<String> SINGLE_ARG_MESSAGES = new HashSet<>(Arrays.asList(new String[] {
			IDE_CHANGE_CATEGORY, IDE_SET_SPRITE_TAB, IDE_TOGGLE_APP_MODE, HELP_BUTTON_TOGGLED
	}));

	private final static DateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");



	public final int id;
	public final Snapshot snapshot;
	public final Date timestamp;
	public final String sessionID, message, data;

	@SuppressWarnings("unused")
	private AttemptAction() {
		this(0, null, null, null, null, null, null);
	}

	public AttemptAction(int id, String attemptID, Date timestamp, String sessionID, String message,
			String data, String snapshotXML) {
		this.id = id;
		this.timestamp = timestamp;
		this.sessionID = sessionID;
		this.message = message;
		this.data = data;
		String name = attemptID;
		synchronized (format) {
			name += timestamp == null ? null : (": " + format.format(timestamp));
		}
		this.snapshot = Snapshot.parse(name, snapshotXML);
	}

	@Override
	public int compareTo(AttemptAction o) {
		if (timestamp == null) return o == null || o.timestamp == null ? 0 : -1;
		return timestamp.compareTo(o.timestamp);
	}
}
