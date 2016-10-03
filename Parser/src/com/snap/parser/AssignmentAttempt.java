package com.snap.parser;

import java.util.Iterator;
import java.util.LinkedList;

import com.snap.data.Snapshot;

public class AssignmentAttempt implements Iterable<AttemptAction>, IVersioned {

	public final static String VERSION = "1.5.1";

	public final static int UNKNOWN = -1;
	public final static int NOT_SUBMITTED = -2;

	public final LinkedList<AttemptAction> rows = new LinkedList<AttemptAction>();
	public final Grade grade;
	/** Whether the attempt was ever exported from Snap. */
	public boolean exported;
	/** The Snapshot which was actually submitted, if known. */
	public Snapshot submittedSnapshot;
	/** The ID of the logging row in which the submitted snapshot was exported. */
	public int submittedActionID = UNKNOWN;

	private String version = VERSION;

	@SuppressWarnings("unused")
	private AssignmentAttempt() {
		this(null);
	}

	public AssignmentAttempt(Grade grade) {
		this.grade = grade;
	}

	public void add(AttemptAction row) {
		rows.add(row);
	}

	public int size() {
		return rows.size();
	}

	@Override
	public Iterator<AttemptAction> iterator() {
		return rows.iterator();
	}

	@Override
	public boolean isUpToDate() {
		return version.equals(VERSION);
	}
}
