package com.snap.parser;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.snap.data.Snapshot;

public class AttemptAction implements Serializable, Comparable<AttemptAction> {
	private static final long serialVersionUID = 1L;

	public final static String IDE_EXPORT_PROJECT = "IDE.exportProject";
	public final static String BLOCK_GRABBED = "Block.grabbed";
	public final static String BLOCK_EDITOR_START = "BlockEditor.start";
	public final static String BLOCK_EDITOR_OK = "BlockEditor.ok";
	public final static String SHOW_SCRIPT_HINT = "SnapDisplay.showScriptHint";
	public final static String SHOW_BLOCK_HINT = "SnapDisplay.showBlockHint";
	public final static String SHOW_STRUCTURE_HINT = "SnapDisplay.showStructureHint";
	public final static String HINT_DIALOG_DONE = "HintDialogBox.done";
	public final static String PROCESS_HINTS = "HintProvider.processHints";
	public final static String GREEN_FLAG_RUN = "IDE.greenFlag";
	public final static String BLOCK_RUN = "Block.clickRun";

	public final static Set<String> SHOW_HINT_MESSAGES = new HashSet<>(Arrays.asList(new String[] {
			SHOW_SCRIPT_HINT, SHOW_BLOCK_HINT, SHOW_STRUCTURE_HINT
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
