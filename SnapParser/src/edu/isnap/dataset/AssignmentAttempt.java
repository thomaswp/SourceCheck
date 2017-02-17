package edu.isnap.dataset;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.isnap.parser.SnapParser;
import edu.isnap.parser.elements.Snapshot;

public class AssignmentAttempt implements Iterable<AttemptAction> {

	public final static int UNKNOWN = -1;
	public final static int NOT_SUBMITTED = -2;

	public final String id;
	public final ActionRows rows = new ActionRows();
	public final Grade grade;
	/** Whether the attempt was ever exported from Snap. */
	public boolean exported;
	/** The Snapshot which was actually submitted, if known. */
	public Snapshot submittedSnapshot;
	/** The ID of the logging row in which the submitted snapshot was exported. */
	public int submittedActionID = UNKNOWN;
	/** Total active time (in seconds) the student spent working on the assignment */
	public int totalActiveTime;
	/** Total idle time (in seconds) the student spent working on the assignment */
	public int totalIdleTime;
	/** The number of work segments, divided by {@link SnapParser#SKIP_DURATION} */
	public int timeSegments;

	/** Returns true if this attempt is known not to have been submitted for grading. */
	public boolean isUnsubmitted() {
		return submittedActionID == NOT_SUBMITTED;
	}

	/**
	 * Returns true if this attempt is know to have been submitted for grading or this is unknown, but it was exported.
	 */
	public boolean isLikelySubmitted() {
		return submittedActionID >= 0 || (submittedActionID == UNKNOWN && exported);
	}

	public boolean isSubmitted() {
		return submittedActionID >= 0;
	}

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

	public String userID() {
		return rows.userID;
	}

	@SuppressWarnings("serial")
	public static class ActionRows implements IVersioned, Serializable, Iterable<AttemptAction> {

		/**
		 * Version of the AttemptAction and its stored classes, such as Snapshot.
		 * Change it when they should be reloaded and the cache invalidated.
		 */
		public final static String VERSION = "1.1.0";
		public final List<AttemptAction> rows = new ArrayList<>();

		public String userID;
		private String version = VERSION;

		@Override
		public boolean isUpToDate() {
			return version.equals(VERSION);
		}

		// List wrapper methods

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

		public AttemptAction get(int i) {
			return rows.get(i);
		}

		public AttemptAction getFirst() {
			return rows.get(0);
		}

		public AttemptAction getLast() {
			return rows.get(rows.size() - 1);
		}
	}
}
