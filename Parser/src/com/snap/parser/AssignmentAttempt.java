package com.snap.parser;

import java.util.Iterator;
import java.util.LinkedList;

public class AssignmentAttempt implements Iterable<AttemptAction>, IVersioned {

	public final static String VERSION = "1.4.0";

	public final LinkedList<AttemptAction> rows = new LinkedList<AttemptAction>();
	public final Grade grade;
	/** Whether the attempt was actually submitted for a grade; null if unknown. */
	public final Boolean submitted;
	/** Whether the attempt was ever exported from Snap. */
	public boolean exported;

	private String version = VERSION;

	@SuppressWarnings("unused")
	private AssignmentAttempt() {
		this(null, null);
	}

	public AssignmentAttempt(Grade grade, Boolean submitted) {
		this.grade = grade;
		this.submitted = submitted;
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
