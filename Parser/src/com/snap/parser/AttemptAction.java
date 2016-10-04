package com.snap.parser;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.snap.data.Snapshot;

public class AttemptAction implements Serializable, Comparable<AttemptAction> {
	private static final long serialVersionUID = 1L;

	public final static String IDE_EXPORT_PROJECT = "IDE.exportProject";
	public final static String BLOCK_GRABBED = "Block.grabbed";
	public final static String BLOCK_EDITOR_START = "BlockEditor.start";
	public final static String BLOCK_EDITOR_OK = "BlockEditor.ok";

	private final static DateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

	public final int id;
	public final Snapshot snapshot;
	public final Date timestamp;
	public final String message, data;

	@SuppressWarnings("unused")
	private AttemptAction() {
		this(0, null, null, null, null, null);
	}

	public AttemptAction(int id, String attemptID, Date timestamp, String message, String data,
			String snapshotXML) {
		this.id = id;
		this.timestamp = timestamp;
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
