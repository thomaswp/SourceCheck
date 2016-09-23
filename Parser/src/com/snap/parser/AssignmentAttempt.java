package com.snap.parser;

import java.util.Iterator;
import java.util.LinkedList;

public class AssignmentAttempt implements Iterable<AttemptAction>, IVersioned {

	public final static String VERSION = "1.3.0";

	public final LinkedList<AttemptAction> rows = new LinkedList<AttemptAction>();
	public final Grade grade;
	public boolean exported;

	private String version = VERSION;

	@SuppressWarnings("unused")
	private AssignmentAttempt() {
		this.grade = null;
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
