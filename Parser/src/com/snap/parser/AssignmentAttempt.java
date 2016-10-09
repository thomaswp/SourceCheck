package com.snap.parser;

import java.util.Iterator;
import java.util.LinkedList;

import com.snap.data.Snapshot;

public class AssignmentAttempt implements Iterable<AttemptAction> {

	public final static int UNKNOWN = -1;
	public final static int NOT_SUBMITTED = -2;

	// TODO: add attemptID

	public final String id;
	public final ActionRows rows = new ActionRows();
	public final Grade grade;
	/** Whether the attempt was ever exported from Snap. */
	public boolean exported;
	/** The Snapshot which was actually submitted, if known. */
	public Snapshot submittedSnapshot;
	/** The ID of the logging row in which the submitted snapshot was exported. */
	public int submittedActionID = UNKNOWN;

	@SuppressWarnings("unused")
	private AssignmentAttempt() {
		this(null, null);
	}

	public AssignmentAttempt(String id, Grade grade) {
		this.id = id;
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

	@SuppressWarnings("serial")
	public static class ActionRows extends LinkedList<AttemptAction> implements IVersioned {

		/**
		 * Version of the AttemptAction and its stored classes, such as Snapshot.
		 * Change it when they should be reloaded and the cache invalidated.
		 */
		public final static String VERSION = "1.0.2";

		private String version = VERSION;

		@Override
		public boolean isUpToDate() {
			return version.equals(VERSION);
		}

	}
}
